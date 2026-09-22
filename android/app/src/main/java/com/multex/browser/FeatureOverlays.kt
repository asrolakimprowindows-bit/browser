package com.multex.browser
import androidx.compose.foundation.Image

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.tween

/*
 * UI for the feature pack: profile button & sheet, language picker, find-in-page bar,
 * history / downloads / extensions / site-settings sheets, and the app-link / permission dialogs.
 * Built on the shared primitives (OverlaySheet, GlassSwitch, Segmented, PillButton, ...).
 */

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* ---------- Profile avatar button (home header, top right) ---------- */

@Composable
fun ProfileAvatarButton(
    emoji: String,
    imagePath: String,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val bitmap = remember(imagePath) {
        imagePath.takeIf { it.isNotBlank() }?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() }
    }
    Box(
        Modifier
            .size(44.dp)
            .liquidGlass(CircleShape)
            .press(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null && !bitmap.isRecycled) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = contentDescription,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.size(40.dp).clip(CircleShape),
            )
        } else {
            Text(emoji, fontSize = 20.sp)
        }
    }
}

/* ---------- Language picker (22 languages would never fit a Segmented) ---------- */

@Composable
fun LanguagePicker(value: Lang, onChange: (Lang) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .glass(12.dp)
                .press { open = !open }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Filled.Language, contentDescription = null, tint = Palette.Pink, modifier = Modifier.size(16.dp))
            Text(value.label, color = Palette.Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Icon(
                if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = Palette.InkMuted,
                modifier = Modifier.size(18.dp),
            )
        }
        if (open) {
            Column(
                Modifier.fillMaxWidth().glass(12.dp).heightIn(max = 260.dp).verticalScroll(rememberScrollState()).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Lang.entries.toList().chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { l ->
                            val sel = l == value
                            Box(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (sel) Palette.Pink else Palette.Ink.copy(alpha = 0.08f))
                                    .press(role = Role.RadioButton) {
                                        onChange(l)
                                        open = false
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    l.label,
                                    color = if (sel) Palette.OnAccent else Palette.InkMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/* ---------- Find in page bar ---------- */

@Composable
fun FindBar(
    lang: Lang,
    info: String,
    onQuery: (String) -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onClose: () -> Unit,
) {
    var q by remember { mutableStateOf("") }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = 6.dp)
            .liquidGlass(999.dp)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = Palette.InkMuted, modifier = Modifier.size(16.dp))
        Box(Modifier.weight(1f)) {
            if (q.isEmpty()) {
                Text(tx(lang, "Find in page", "Cari di halaman"), color = Palette.InkMuted, fontSize = 13.sp)
            }
            BasicTextField(
                value = q,
                onValueChange = {
                    q = it
                    onQuery(it)
                },
                singleLine = true,
                textStyle = TextStyle(color = Palette.Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                cursorBrush = SolidColor(Palette.Pink),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onNext() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (info.isNotEmpty()) {
            Text(info, color = Palette.Sky, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        RoundIconButton(Icons.Filled.KeyboardArrowUp, contentDescription = tx(lang, "Previous", "Sebelumnya"), onClick = onPrev, enabled = q.isNotEmpty())
        RoundIconButton(Icons.Filled.KeyboardArrowDown, contentDescription = tx(lang, "Next", "Berikutnya"), onClick = onNext, enabled = q.isNotEmpty())
        RoundIconButton(Icons.Filled.Close, contentDescription = tx(lang, "Close", "Tutup"), onClick = onClose)
    }
}

/* ---------- History ---------- */

@Composable
fun BoxScope.HistoryOverlay(
    lang: Lang,
    entries: List<HistoryEntry>,
    onOpen: (String) -> Unit,
    onRemove: (HistoryEntry) -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
) {
    var confirm by remember { mutableStateOf(false) }
    OverlaySheet(
        title = tx(lang, "History", "Riwayat"),
        subtitle = tx(lang, "${entries.size} pages", "${entries.size} halaman"),
        onClose = onClose,
        footer = {
            PillButtonFull(
                if (confirm) tx(lang, "Sure? Tap again to clear", "Yakin? Ketuk lagi buat hapus") else tx(lang, "Clear all history", "Hapus semua riwayat"),
                Icons.Filled.Delete,
            ) {
                if (confirm) {
                    onClear()
                    confirm = false
                } else {
                    confirm = true
                }
            }
        },
    ) {
        if (entries.isEmpty()) {
            EmptyHint(tx(lang, "No history yet. Pages you visit land here.", "Belum ada riwayat. Halaman yang kamu buka mampir ke sini."))
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            entries.forEach { e ->
                Row(
                    Modifier.fillMaxWidth().glass(14.dp).press { onOpen(e.url) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    LetterTile(e.title.ifBlank { e.host }, tintFor(e.host), TileSize.SM)
                    Column(Modifier.weight(1f)) {
                        Text(e.title.ifBlank { e.host }, color = Palette.Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            e.host + " · " + relativeTime(lang, e.time),
                            color = Palette.InkMuted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    RoundIconButton(Icons.Filled.Close, contentDescription = tx(lang, "Remove", "Hapus"), onClick = { onRemove(e) })
                }
            }
        }
    }
}

/* ---------- Downloads ---------- */

@Composable
fun BoxScope.DownloadsOverlay(
    lang: Lang,
    items: List<DownloadEntry>,
    onRedownload: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
) {
    OverlaySheet(
        title = tx(lang, "Downloads", "Unduhan"),
        subtitle = tx(lang, "${items.size} files", "${items.size} file"),
        onClose = onClose,
        footer = if (items.isNotEmpty()) ({
            PillButtonFull(tx(lang, "Clear list", "Bersihkan daftar"), Icons.Filled.Delete) { onClear() }
        }) else null,
    ) {
        if (items.isEmpty()) {
            EmptyHint(tx(lang, "Nothing downloaded yet.", "Belum ada unduhan."))
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEach { d ->
                Row(
                    Modifier.fillMaxWidth().glass(14.dp).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Palette.Sky.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null, tint = Palette.Sky, modifier = Modifier.size(16.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(d.name, color = Palette.Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            (if (d.status == "external") tx(lang, "External downloader", "Downloader eksternal") else tx(lang, "Downloads folder", "Folder Download")) +
                                " · " + relativeTime(lang, d.time),
                            color = Palette.InkMuted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    RoundIconButton(Icons.Filled.Refresh, contentDescription = tx(lang, "Download again", "Unduh lagi"), onClick = { onRedownload(d.url) })
                    RoundIconButton(Icons.Filled.Close, contentDescription = tx(lang, "Remove", "Hapus"), onClick = { onRemove(d.id) })
                }
            }
        }
    }
}

/* ---------- Extensions (userscripts) ---------- */

@Composable
fun BoxScope.ExtensionsOverlay(
    lang: Lang,
    scripts: List<ExtScript>,
    onToggle: (String) -> Unit,
    onRemove: (String) -> Unit,
    onAdd: (String, String, String) -> Unit,
    onClose: () -> Unit,
) {
    var showForm by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var hosts by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }

    OverlaySheet(
        title = tx(lang, "Extensions", "Ekstensi"),
        subtitle = tx(
            lang,
            "Userscripts that run on matching sites, like Firefox add-ons",
            "Userscript yang jalan di situs yang cocok, mirip add-on Firefox",
        ),
        onClose = onClose,
        footer = {
            if (showForm) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PillButton(tx(lang, "Cancel", "Batal"), Icons.Filled.Close) { showForm = false }
                    PillButton(tx(lang, "Add script", "Tambah script"), Icons.Filled.Check, accent = true) {
                        if (code.isNotBlank()) {
                            onAdd(name, hosts, code)
                            name = ""; hosts = ""; code = ""
                            showForm = false
                        }
                    }
                }
            } else {
                PillButtonFull(tx(lang, "Add userscript", "Tambah userscript"), Icons.Filled.Add, accent = true) { showForm = true }
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (showForm) {
                Column(Modifier.fillMaxWidth().glass(16.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassField(value = name, onChange = { name = it }, placeholder = tx(lang, "Name (e.g. Dark TikTok)", "Nama (mis. TikTok Gelap)"))
                    GlassField(value = hosts, onChange = { hosts = it }, placeholder = tx(lang, "Sites: * or tiktok.com, x.com", "Situs: * atau tiktok.com, x.com"))
                    GlassField(value = code, onChange = { code = it }, placeholder = "JavaScript…")
                    Text(
                        tx(lang, "Runs after every page load on the matching sites.", "Jalan setiap halaman selesai dimuat di situs yang cocok."),
                        color = Palette.InkMuted,
                        fontSize = 11.sp,
                    )
                }
            }
            scripts.forEach { s ->
                Row(
                    Modifier.fillMaxWidth().glass(14.dp).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Palette.Lavender.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Extension, contentDescription = null, tint = Palette.Lavender, modifier = Modifier.size(16.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(s.name, color = Palette.Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            s.hosts.joinToString(", ") + if (s.builtin) " · " + tx(lang, "built-in", "bawaan") else "",
                            color = Palette.InkMuted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (!s.builtin) {
                        RoundIconButton(Icons.Filled.Delete, contentDescription = tx(lang, "Delete", "Hapus"), onClick = { onRemove(s.id) })
                    }
                    GlassSwitch(s.enabled, { onToggle(s.id) }, s.name)
                }
            }
        }
    }
}

/* ---------- Site settings (per-site mic / camera / location) ---------- */

@Composable
fun BoxScope.SiteSettingsOverlay(
    lang: Lang,
    rules: Map<String, SiteRule>,
    currentHost: String?,
    onSet: (String, SiteRule) -> Unit,
    onRemove: (String) -> Unit,
    onClose: () -> Unit,
) {
    OverlaySheet(
        title = tx(lang, "Site settings", "Pengaturan situs"),
        subtitle = tx(lang, "Mic, camera & location rules per site", "Aturan mic, kamera & lokasi per situs"),
        onClose = onClose,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (currentHost != null && rules[currentHost] == null) {
                Text(
                    tx(lang, "Nothing saved for $currentHost yet — it will ask when the site requests access.", "Belum ada aturan buat $currentHost — nanti ditanya pas situsnya minta akses."),
                    color = Palette.InkMuted,
                    fontSize = 12.sp,
                )
            }
            if (rules.isEmpty()) {
                EmptyHint(tx(lang, "No site rules yet. They appear after you allow or deny a request.", "Belum ada aturan situs. Muncul setelah kamu izinkan atau tolak permintaan."))
            }
            rules.forEach { (host, rule) ->
                Column(Modifier.fillMaxWidth().glass(16.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Shield, contentDescription = null, tint = Palette.Sky, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(host, color = Palette.Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        RoundIconButton(Icons.Filled.Delete, contentDescription = tx(lang, "Reset $host", "Reset $host"), onClick = { onRemove(host) })
                    }
                    PermRow(Icons.Filled.Mic, tx(lang, "Microphone", "Mikrofon"), rule.mic) { onSet(host, rule.copy(mic = it)) }
                    PermRow(Icons.Filled.Videocam, tx(lang, "Camera", "Kamera"), rule.cam) { onSet(host, rule.copy(cam = it)) }
                    PermRow(Icons.Filled.LocationOn, tx(lang, "Location", "Lokasi"), rule.loc) { onSet(host, rule.copy(loc = it)) }
                }
            }
        }
    }
}

@Composable
private fun PermRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: Perm, onChange: (Perm) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, tint = Palette.InkMuted, modifier = Modifier.size(14.dp))
            Text(label, color = Palette.InkMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Segmented(
            value = value,
            options = listOf(Perm.ASK to "Ask", Perm.ALLOW to "Allow", Perm.DENY to "Deny"),
            onChange = onChange,
        )
    }
}

/* ---------- Profile ---------- */

private val PROFILE_EMOJIS = listOf("🦊", "🐼", "🐱", "🐰", "🐻", "🐸", "🦁", "🐯", "🦄", "🐲", "🌸", "⭐")

@Composable
fun BoxScope.ProfileOverlay(
    lang: Lang,
    settings: Settings,
    tabCount: Int,
    sessionCount: Int,
    historyCount: Int,
    onChange: ((Settings) -> Settings) -> Unit,
    onChooseImage: () -> Unit,
    onRemoveImage: () -> Unit,
    onOpenSettings: () -> Unit,
    onClearCookies: () -> Unit,
    onClose: () -> Unit,
) {
    val profileFont = when (settings.profileFont) {
        ProfileFont.FREDOKA -> Fonts.Display
        ProfileFont.SERIF -> FontFamily.Serif
        ProfileFont.MONO -> FontFamily.Monospace
        ProfileFont.NUNITO -> Fonts.Sans
    }

    OverlaySheet(
        title = tx(lang, "Profile", "Profil"),
        subtitle = "Multex Browser",
        onClose = onClose,
        footer = {
            PillButtonFull(tx(lang, "Open settings", "Buka pengaturan"), LucideSettings2, accent = true) { onOpenSettings() }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                val avatarBitmap = remember(settings.profileImagePath) {
                    settings.profileImagePath
                        .takeIf { it.isNotBlank() }
                        ?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() }
                }
                Box(
                    Modifier
                        .size(78.dp)
                        .accent(CircleShape)
                        .padding(3.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(72.dp).clip(CircleShape).background(Palette.Ink.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
                        if (avatarBitmap != null && !avatarBitmap.isRecycled) {
                            Image(
                                bitmap = avatarBitmap.asImageBitmap(),
                                contentDescription = tx(lang, "Profile photo", "Foto profil"),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().fillMaxSize().clip(CircleShape),
                            )
                        } else {
                            Text(settings.profileEmoji, fontSize = 34.sp)
                        }
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        settings.profileName,
                        color = Palette.Ink,
                        fontFamily = profileFont,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        tx(lang, "Local profile — nothing leaves this device", "Profil lokal — tidak ada yang keluar dari HP ini"),
                        color = Palette.InkMuted,
                        fontSize = 11.sp,
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    PillButtonFull(tx(lang, "Upload photo", "Upload foto"), Icons.Filled.Add, accent = true, onClick = onChooseImage)
                }
                if (settings.profileImagePath.isNotBlank()) {
                    Box(Modifier.weight(1f)) {
                        PillButtonFull(tx(lang, "Use emoji", "Pakai emoji"), Icons.Filled.Refresh, onClick = onRemoveImage)
                    }
                }
            }
            Text(
                tx(
                    lang,
                    "Photos are automatically center-cropped to a square and kept only inside this app.",
                    "Foto otomatis di-crop dari tengah jadi kotak dan disimpan hanya di aplikasi ini.",
                ),
                color = Palette.InkMuted,
                fontSize = 10.sp,
            )

            SectionTitle(tx(lang, "Display name", "Nama tampilan"), first = true)
            GlassField(
                value = settings.profileName,
                onChange = { v -> onChange { it.copy(profileName = v.take(32)) } },
                placeholder = tx(lang, "Your name", "Namamu"),
                imeAction = ImeAction.Done,
            )

            SectionTitle(tx(lang, "Profile font", "Font profil"))
            Segmented(
                value = settings.profileFont,
                options = ProfileFont.entries.map { it to it.label },
                onChange = { v -> onChange { it.copy(profileFont = v) } },
            )

            SectionTitle(tx(lang, "Avatar", "Avatar"))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PROFILE_EMOJIS.forEach { e ->
                    val sel = e == settings.profileEmoji && settings.profileImagePath.isBlank()
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (sel) Palette.Pink.copy(alpha = 0.25f) else Palette.Ink.copy(alpha = 0.06f))
                            .then(if (sel) Modifier.border(2.dp, Palette.Pink, CircleShape) else Modifier)
                            .press { onChange { it.copy(profileEmoji = e, profileImagePath = "") } },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(e, fontSize = 20.sp)
                    }
                }
            }

            SectionTitle(tx(lang, "Stats", "Statistik"))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatChip("$tabCount", tx(lang, "tabs", "tab"), Modifier.weight(1f))
                StatChip("$sessionCount", tx(lang, "sessions", "sesi"), Modifier.weight(1f))
                StatChip("$historyCount", tx(lang, "history", "riwayat"), Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatChip(settings.theme.label, tx(lang, "theme", "tema"), Modifier.weight(1f))
                StatChip(settings.language.label, tx(lang, "language", "bahasa"), Modifier.weight(1f))
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFFF6B8A).copy(alpha = 0.15f))
                    .press(onClick = onClearCookies)
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    tx(lang, "Clear cookies & cache (this session)", "Hapus cookie & cache (sesi ini)"),
                    color = Color(0xFFFF6B8A),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun StatChip(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier.glass(14.dp).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = Palette.Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, color = Palette.InkMuted, fontSize = 10.sp)
    }
}

/* ---------- Centered dialog scaffold ---------- */

@Composable
private fun BoxScope.CenterDialog(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).press(role = Role.Image, onClick = onDismiss),
    )
    AnimatedVisibility(
        visible = true,
        modifier = Modifier.align(Alignment.Center),
        enter = fadeIn(tween(Motion.ms(190))) + scaleIn(
            initialScale = 0.86f,
            animationSpec = tween(Motion.ms(240)),
        ),
    ) {
        Column(
            Modifier
                .padding(horizontal = 32.dp)
                .glassStrong(24.dp)
                .press(role = Role.Image) { }
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content()
        }
    }
}

/* ---------- Open-in-app prompt (YouTube, Discord, ...) ---------- */

@Composable
fun BoxScope.AppLinkSheet(lang: Lang, url: String, appName: String, onPick: (Boolean) -> Unit) {
    CenterDialog(onDismiss = { onPick(false) }) {
        Text("📱", fontSize = 34.sp)
        Text(
            tx(lang, "Open in $appName?", "Buka di $appName?"),
            color = Palette.Ink,
            fontFamily = Fonts.Display,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Text(
            hostOf(url),
            color = Palette.InkMuted,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) {
                PillButtonFull(tx(lang, "Browser", "Browser"), null) { onPick(false) }
            }
            Box(Modifier.weight(1f)) {
                PillButtonFull(tx(lang, "Open app", "Buka aplikasi"), Icons.AutoMirrored.Filled.OpenInNew, accent = true) { onPick(true) }
            }
        }
    }
}

/* ---------- Runtime permission prompts (mic / camera / location) ---------- */

@Composable
fun BoxScope.PermissionSheet(lang: Lang, host: String, resources: List<String>, onAnswer: (Boolean) -> Unit) {
    val labels = resources.map {
        when (it) {
            android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE -> tx(lang, "microphone", "mikrofon")
            android.webkit.PermissionRequest.RESOURCE_VIDEO_CAPTURE -> tx(lang, "camera", "kamera")
            else -> it.substringAfterLast('_').lowercase()
        }
    }.distinct()
    CenterDialog(onDismiss = { onAnswer(false) }) {
        Icon(Icons.Filled.Mic, contentDescription = null, tint = Palette.Pink, modifier = Modifier.size(30.dp))
        Text(
            tx(lang, "Allow $host to use your ${labels.joinToString(" & ")}?", "Izinkan $host memakai ${labels.joinToString(" & ")} kamu?"),
            color = Palette.Ink,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            tx(lang, "Your choice is remembered in Site settings.", "Pilihanmu diingat di Pengaturan situs."),
            color = Palette.InkMuted,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) { PillButtonFull(tx(lang, "Deny", "Tolak"), null) { onAnswer(false) } }
            Box(Modifier.weight(1f)) { PillButtonFull(tx(lang, "Allow", "Izinkan"), Icons.Filled.Check, accent = true) { onAnswer(true) } }
        }
    }
}

@Composable
fun BoxScope.GeoSheet(lang: Lang, origin: String, onAnswer: (Boolean) -> Unit) {
    CenterDialog(onDismiss = { onAnswer(false) }) {
        Icon(Icons.Filled.LocationOn, contentDescription = null, tint = Palette.Pink, modifier = Modifier.size(30.dp))
        Text(
            tx(lang, "Share your location with ${hostOf(origin)}?", "Bagikan lokasimu ke ${hostOf(origin)}?"),
            color = Palette.Ink,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) { PillButtonFull(tx(lang, "Deny", "Tolak"), null) { onAnswer(false) } }
            Box(Modifier.weight(1f)) { PillButtonFull(tx(lang, "Allow", "Izinkan"), Icons.Filled.Check, accent = true) { onAnswer(true) } }
        }
    }
}

/* ---------- Shared bits ---------- */

@Composable
private fun EmptyHint(text: String) {
    Text(
        text,
        color = Palette.InkMuted,
        fontSize = 13.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
    )
}
