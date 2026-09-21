package com.multex.browser

/* Port of SettingsOverlay / CreditCard from components/browser/overlays.tsx (Gemini key field replaced by OpenRouter). */

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp



@Composable
fun BoxScope.SettingsOverlay(
    lang: Lang,
    settings: Settings,
    activeSessionName: String,
    onChange: ((Settings) -> Settings) -> Unit,
    onDirect: () -> Unit,
    onClearCookies: () -> Unit,
    onClose: () -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }

    OverlaySheet(title = tx(lang, "Settings", "Pengaturan"), subtitle = "Multex Browser", onClose = onClose) {
        SectionTitle("Denia", first = true)
        SettingRow(tx(lang, "Show Denia", "Tampilkan Denia"), tx(lang, "Companion on every page", "Teman di setiap halaman")) {
            GlassSwitch(settings.companionEnabled, { v -> onChange { it.copy(companionEnabled = v) } }, "Show Denia")
        }
        SettingRow(tx(lang, "Chatty", "Cerewet"), tx(lang, "Comment on tabs, sessions, and idle time", "Komentar soal tab, sesi, dan saat diam")) {
            GlassSwitch(settings.chatty, { v -> onChange { it.copy(chatty = v) } }, "Chatty")
        }
        SettingRow(tx(lang, "Size", "Ukuran"), stacked = true) {
            Segmented(
                value = settings.companionSize,
                options = listOf(
                    CompanionSize.SM to tx(lang, "Small", "Kecil"),
                    CompanionSize.MD to tx(lang, "Medium", "Sedang"),
                    CompanionSize.LG to tx(lang, "Large", "Besar"),
                ),
                onChange = { v -> onChange { it.copy(companionSize = v) } },
            )
        }
        SettingRow(
            tx(lang, "Companion pet", "Hewan peliharaan"),
            tx(lang, "A little friend who hops beside her", "Teman kecil yang lompat di sampingnya"),
            stacked = true,
        ) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PetId.entries.forEach { pet ->
                    val selected = settings.pet == pet
                    val shape = RoundedCornerShape(16.dp)
                    Column(
                        Modifier
                            .width(72.dp)
                            .clip(shape)
                            .background(if (selected) Palette.Pink.copy(alpha = 0.2f) else Palette.Ink.copy(alpha = 0.05f))
                            .then(if (selected) Modifier.border(2.dp, Palette.Pink, shape) else Modifier)
                            .press { onChange { it.copy(pet = pet) } }
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                            val res = pet.res
                            if (res != null) {
                                Image(painterResource(res), contentDescription = null, modifier = Modifier.height(48.dp))
                            } else {
                                Text("–", color = Palette.InkMuted, fontSize = 12.sp)
                            }
                        }
                        Text(pet.label, color = Palette.Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Row(
            Modifier
                .padding(top = 4.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Palette.Ink.copy(alpha = 0.1f))
                .press(onClick = onDirect)
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.GpsFixed, contentDescription = null, tint = Palette.Pink, modifier = Modifier.size(16.dp))
            Text(tx(lang, "Enter direct mode", "Masuk mode arah"), color = Palette.Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }

        SectionTitle("Denia AI")
        SettingRow(
            tx(lang, "AI provider", "Penyedia AI"),
            tx(lang, "Local commands always work without an API.", "Perintah lokal tetap bisa dipakai tanpa API."),
            stacked = true,
        ) {
            Segmented(
                value = settings.aiProvider,
                options = listOf(
                    AiProvider.OPENROUTER to "OpenRouter",
                    AiProvider.OPENAI_COMPATIBLE to "OpenAI compat.",
                ),
                onChange = { provider -> onChange { it.copy(aiProvider = provider) } },
            )
        }
        when (settings.aiProvider) {
            AiProvider.OPENROUTER -> SettingRow(
                "OpenRouter",
                tx(lang, "Lets Denia answer free-form questions. The key is stored only on this device.", "Biar Denia bisa jawab pertanyaan bebas. Key disimpan cuma di HP ini."),
                stacked = true,
            ) {
                OpenRouterFields(
                    apiKey = settings.openRouterKey,
                    model = settings.openRouterModel,
                    lang = lang,
                    onApiKeyChange = { v -> onChange { it.copy(openRouterKey = v) } },
                    onModelChange = { v -> onChange { it.copy(openRouterModel = v) } },
                )
            }
            AiProvider.OPENAI_COMPATIBLE -> SettingRow(
                tx(lang, "OpenAI-compatible API", "API kompatibel OpenAI"),
                tx(
                    lang,
                    "Use any Chat Completions-compatible server. The API key is optional for local servers.",
                    "Pakai server apa pun yang kompatibel dengan Chat Completions. API key opsional untuk server lokal.",
                ),
                stacked = true,
            ) {
                OpenAiCompatibleFields(
                    baseUrl = settings.openAiBaseUrl,
                    apiKey = settings.openAiApiKey,
                    model = settings.openAiModel,
                    lang = lang,
                    onBaseUrlChange = { v -> onChange { it.copy(openAiBaseUrl = v) } },
                    onApiKeyChange = { v -> onChange { it.copy(openAiApiKey = v) } },
                    onModelChange = { v -> onChange { it.copy(openAiModel = v) } },
                )
            }
        }

        SectionTitle(tx(lang, "App", "Aplikasi"))
        SettingRow(
            tx(lang, "Language", "Bahasa"),
            tx(lang, "Denia and the interface follow this", "Denia dan tampilan mengikuti ini"),
            stacked = true,
        ) {
            Segmented(
                value = settings.language,
                options = Lang.entries.map { it to it.label },
                onChange = { v -> onChange { it.copy(language = v) } },
            )
        }

        SectionTitle(tx(lang, "Appearance", "Tampilan"))
        SettingRow(tx(lang, "Theme", "Tema"), stacked = true) {
            Segmented(
                value = settings.theme,
                options = ThemeId.entries.map { it to it.label },
                onChange = { v -> onChange { it.copy(theme = v) } },
            )
        }

        SectionTitle(tx(lang, "Search", "Pencarian"))
        SettingRow(tx(lang, "Default engine", "Mesin bawaan"), stacked = true) {
            Segmented(
                value = settings.searchEngine,
                options = ENGINES.map { it to it.label },
                onChange = { v -> onChange { it.copy(searchEngine = v) } },
            )
        }

        SectionTitle(tx(lang, "Privacy", "Privasi"))
        SettingRow(tx(lang, "Block trackers", "Blokir pelacak"), tx(lang, "Shield counter in the address bar", "Penghitung perisai di bar alamat")) {
            GlassSwitch(settings.blockTrackers, { v -> onChange { it.copy(blockTrackers = v) } }, "Block trackers")
        }
        SettingRow("HTTPS only", tx(lang, "Upgrade insecure requests", "Naikkan permintaan tidak aman ke HTTPS")) {
            GlassSwitch(settings.httpsOnly, { v -> onChange { it.copy(httpsOnly = v) } }, "HTTPS only")
        }

        SectionTitle(tx(lang, "Browser", "Peramban"))
        SettingRow(tx(lang, "Desktop site", "Situs desktop"), tx(lang, "Ask sites for their desktop version", "Minta versi desktop dari situs")) {
            GlassSwitch(settings.desktopSite, { v -> onChange { it.copy(desktopSite = v) } }, "Desktop site")
        }
        SettingRow(
            tx(lang, "Fullscreen orb detail", "Detail orb layar penuh"),
            tx(lang, "Black-hole & laser animation quality", "Kualitas animasi black-hole & laser"),
            stacked = true,
        ) {
            Segmented(
                value = settings.orbDetail,
                options = OrbDetail.entries.map { it to it.label },
                onChange = { v -> onChange { it.copy(orbDetail = v) } },
            )
        }
        SettingRow(
            tx(lang, "Session cookies", "Cookie sesi"),
            tx(lang, "Only affects \"$activeSessionName\". Other sessions keep their logins.", "Cuma untuk \"$activeSessionName\". Sesi lain tetap login."),
            stacked = true,
        ) {
            val danger = Color(0xFFFF6B8A)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(danger.copy(alpha = if (confirmClear) 0.3f else 0.15f))
                    .press {
                        if (confirmClear) {
                            onClearCookies()
                            confirmClear = false
                        } else {
                            confirmClear = true
                        }
                    }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    if (confirmClear) tx(lang, "Sure? Tap again to clear", "Yakin? Ketuk lagi buat hapus")
                    else tx(lang, "Clear cookies & cache", "Hapus cookie & cache"),
                    color = danger,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        SectionTitle(tx(lang, "About", "Tentang"))
        CreditCard(lang)
    }
}

@Composable
private fun OpenAiCompatibleFields(
    baseUrl: String,
    apiKey: String,
    model: String,
    lang: Lang,
    onBaseUrlChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ApiSettingField(
            label = tx(lang, "Base API URL", "Base API URL"),
            value = baseUrl,
            placeholder = "https://api.example.com/v1",
            lang = lang,
            keyboardType = KeyboardType.Uri,
            onChange = onBaseUrlChange,
        )
        ApiSettingField(
            label = tx(lang, "API key (optional)", "API key (opsional)"),
            value = apiKey,
            placeholder = "sk-...",
            lang = lang,
            secret = true,
            keyboardType = KeyboardType.Password,
            onChange = onApiKeyChange,
        )
        ApiSettingField(
            label = tx(lang, "Model", "Model"),
            value = model,
            placeholder = "gpt-4o-mini",
            lang = lang,
            onChange = onModelChange,
        )
        Text(
            tx(
                lang,
                "Use a base URL ending in /v1, or paste the full /chat/completions endpoint.",
                "Pakai base URL yang berakhir /v1, atau tempel endpoint /chat/completions lengkap.",
            ),
            color = Palette.InkMuted,
            fontSize = 11.sp,
            lineHeight = 15.sp,
        )
    }
}

@Composable
private fun ApiSettingField(
    label: String,
    value: String,
    placeholder: String,
    lang: Lang,
    secret: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    onChange: (String) -> Unit,
) {
    var draft by remember(value) { mutableStateOf(value) }
    var revealed by remember { mutableStateOf(false) }
    val dirty = draft.trim() != value

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = Palette.InkMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Row(
            Modifier
                .fillMaxWidth()
                .glass(16.dp)
                .padding(start = 12.dp, top = 6.dp, end = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (secret) {
                Icon(
                    Icons.Filled.VpnKey,
                    contentDescription = null,
                    tint = if (value.isNotBlank()) Palette.Pink else Palette.InkMuted,
                    modifier = Modifier.size(16.dp),
                )
            }
            Box(Modifier.weight(1f)) {
                if (draft.isEmpty()) Text(placeholder, color = Palette.InkMuted, fontSize = 13.sp)
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Palette.Ink,
                        fontSize = 13.sp,
                        fontFamily = if (secret) FontFamily.Monospace else FontFamily.Default,
                    ),
                    cursorBrush = SolidColor(Palette.Pink),
                    visualTransformation = if (secret && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onChange(draft.trim()) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { if (!it.isFocused && draft.trim() != value) onChange(draft.trim()) },
                )
            }
            if (secret) {
                RoundIconButton(
                    if (revealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (revealed) tx(lang, "Hide key", "Sembunyikan key") else tx(lang, "Show key", "Tampilkan key"),
                    onClick = { revealed = !revealed },
                )
            }
            if (draft.isNotEmpty() || value.isNotEmpty()) {
                RoundIconButton(
                    Icons.Filled.Close,
                    contentDescription = tx(lang, "Clear", "Hapus"),
                    onClick = {
                        draft = ""
                        onChange("")
                    },
                )
            }
        }
        if (dirty) {
            Text(
                tx(lang, "Press Done to save", "Tekan Selesai untuk simpan"),
                color = Palette.Pink,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun OpenRouterFields(
    apiKey: String,
    model: String,
    lang: Lang,
    onApiKeyChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ApiSettingField(
            label = "OpenRouter API key",
            value = apiKey,
            placeholder = "sk-or-...",
            lang = lang,
            secret = true,
            keyboardType = KeyboardType.Password,
            onChange = onApiKeyChange,
        )
        ApiSettingField(
            label = tx(lang, "Model", "Model"),
            value = model,
            placeholder = OPENROUTER_DEFAULT_MODEL,
            lang = lang,
            onChange = onModelChange,
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(6.dp)
                        .background(if (apiKey.isNotBlank()) Palette.Pink else Palette.Ink.copy(alpha = 0.3f), CircleShape),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (apiKey.isNotBlank()) tx(lang, "OpenRouter connected", "OpenRouter terhubung")
                    else tx(lang, "Offline brain only", "Otak offline saja"),
                    color = if (apiKey.isNotBlank()) Palette.Pink else Palette.InkMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Row(
                Modifier.press {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(OPENROUTER_KEY_URL)))
                    } catch (e: Exception) {
                        // no browser to hand the link to
                    }
                },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(tx(lang, "Get a key", "Ambil key"), color = Palette.InkMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = Palette.InkMuted, modifier = Modifier.size(12.dp))
            }
        }
        Text(
            tx(
                lang,
                "Model ids come from openrouter.ai/models. Leave the model empty to use the default.",
                "ID model ada di openrouter.ai/models. Kosongkan model buat pakai bawaan.",
            ),
            color = Palette.InkMuted,
            fontSize = 11.sp,
            lineHeight = 15.sp,
        )
    }
}

@Composable
private fun CreditCard(lang: Lang) {
    Column(Modifier.padding(top = 4.dp).fillMaxWidth().glass(16.dp).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(48.dp).accent(RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Build, contentDescription = null, tint = Palette.OnAccent, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("SELF BUILD", color = Palette.InkMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text("Shina", color = Palette.Ink, fontFamily = Fonts.Display, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(
                "v1.0",
                color = Palette.InkMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Palette.Ink.copy(alpha = 0.1f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = Palette.GlassBorder)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Multex Browser × Denia", color = Palette.InkMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text(tx(lang, "Handcrafted with", "Dibuat dengan"), color = Palette.InkMuted, fontSize = 12.sp)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Filled.Favorite, contentDescription = "love", tint = Palette.Pink, modifier = Modifier.size(14.dp))
        }
    }
}
