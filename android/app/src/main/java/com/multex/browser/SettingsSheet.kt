package com.multex.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.multex.browser.denia.DeniaPalette
import com.multex.browser.denia.Lang
import com.multex.browser.denia.ThemeId
import com.multex.browser.denia.glass
import com.multex.browser.denia.tx

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    showDenia: Boolean,
    onShowDeniaChange: (Boolean) -> Unit,
    chatty: Boolean,
    onChattyChange: (Boolean) -> Unit,
    petId: String,
    onPetChange: (String) -> Unit,
    sizeId: String,
    onSizeChange: (String) -> Unit,
    engineId: String,
    onEngineChange: (String) -> Unit,
    lang: Lang,
    onLangChange: (Lang) -> Unit,
    theme: ThemeId,
    onThemeChange: (ThemeId) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DeniaPalette.Surface,
        contentColor = DeniaPalette.Ink,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(tx(lang, "Companion", "Teman"), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))

            ToggleRow(
                tx(lang, "Show Denia", "Tampilkan Denia"),
                tx(lang, "Hide her when you need the whole screen.", "Sembunyikan saat butuh layar penuh."),
                showDenia, onShowDeniaChange,
            )
            ToggleRow(
                tx(lang, "Chatty", "Cerewet"),
                tx(lang, "Let her comment while you browse.", "Biarkan dia komentar saat browsing."),
                chatty, onChattyChange,
            )

            SectionLabel(tx(lang, "Pet", "Hewan"))
            ChipRow(PETS.map { it.id to it.label }, petId, onPetChange)

            SectionLabel(tx(lang, "Size", "Ukuran"))
            ChipRow(SIZES.map { it.id to it.label }, sizeId, onSizeChange)

            Spacer(Modifier.height(20.dp))
            Text(tx(lang, "App", "Aplikasi"), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            SectionLabel(tx(lang, "Language", "Bahasa"))
            ChipRow(Lang.entries.map { it.id to it.label }, lang.id) { id -> Lang.entries.firstOrNull { it.id == id }?.let(onLangChange) }

            SectionLabel(tx(lang, "Theme", "Tema"))
            ChipRow(listOf(ThemeId.MIDNIGHT.id to "Midnight", ThemeId.SAKURA.id to "Sakura"), theme.id) { id ->
                ThemeId.entries.firstOrNull { it.id == id }?.let(onThemeChange)
            }

            Spacer(Modifier.height(20.dp))
            Text("Browser", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            SectionLabel(tx(lang, "Search engine", "Mesin pencari"))
            ChipRow(ENGINES.map { it.id to it.label }, engineId, onEngineChange)

            Spacer(Modifier.height(20.dp))
            Text(tx(lang, "About", "Tentang"), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            CreditCard(lang)
        }
    }
}

@Composable
private fun CreditCard(lang: Lang) {
    Column(Modifier.fillMaxWidth().glass(radius = 20.dp).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(48.dp)
                    .background(
                        Brush.linearGradient(listOf(DeniaPalette.Pink, DeniaPalette.Lavender)),
                        RoundedCornerShape(16.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Build, contentDescription = null, tint = DeniaPalette.Midnight)
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "SELF BUILD",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    color = DeniaPalette.InkMuted,
                )
                Text("Shina", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }
            Box(
                Modifier
                    .background(DeniaPalette.Ink.copy(alpha = 0.1f), CircleShape)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text("v1.0", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DeniaPalette.InkMuted)
            }
        }
        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = DeniaPalette.GlassBorder)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Multex Browser × Denia", fontSize = 12.sp, color = DeniaPalette.InkMuted, modifier = Modifier.weight(1f))
            Text(tx(lang, "Handcrafted with", "Dibuat dengan"), fontSize = 12.sp, color = DeniaPalette.InkMuted)
            Spacer(Modifier.size(4.dp))
            Icon(Icons.Filled.Favorite, contentDescription = "love", tint = DeniaPalette.Pink, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, fontSize = 12.sp, color = DeniaPalette.InkMuted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = DeniaPalette.Midnight,
                checkedTrackColor = DeniaPalette.Pink,
            ),
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Spacer(Modifier.height(12.dp))
    Text(
        text.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp,
        color = DeniaPalette.InkMuted,
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun ChipRow(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (id, label) ->
            FilterChip(
                selected = id == selected,
                onClick = { onSelect(id) },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = DeniaPalette.Pink,
                    selectedLabelColor = DeniaPalette.Midnight,
                    labelColor = DeniaPalette.Ink,
                ),
            )
        }
    }
}
