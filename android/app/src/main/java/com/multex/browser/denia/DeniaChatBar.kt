package com.multex.browser.denia

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.multex.browser.AiProvider
import com.multex.browser.Lang
import com.multex.browser.Palette
import com.multex.browser.R
import com.multex.browser.RoundIconButton
import com.multex.browser.SearchEngine
import com.multex.browser.glassStrong
import com.multex.browser.press
import com.multex.browser.tx
import kotlinx.coroutines.launch

private data class Exchange(val you: String, val denia: String)

/**
 * Port of components/denia/denia-chat.tsx. Order of resolution matches the web build:
 * site lookup, then local commands, then the configured AI provider.
 * When Denia proposes opening a site she waits for a No / Yes answer.
 */
@Composable
fun DeniaChatBar(
    lang: Lang,
    engine: SearchEngine,
    aiProvider: AiProvider,
    geminiKey: String,
    openAiBaseUrl: String,
    openAiApiKey: String,
    openAiModel: String,
    context: String,
    onReply: (DeniaReply) -> Unit,
    onOpenUrl: (PendingOpen) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 64.dp,
) {
    var value by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var last by remember { mutableStateOf<Exchange?>(null) }
    var pending by remember { mutableStateOf<PendingOpen?>(null) }
    val focus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    fun show(you: String, reply: DeniaReply) {
        last = Exchange(you, reply.text)
        pending = reply.pending
        // A pending open waits for Yes / No; everything else fires immediately.
        if (reply.pending == null) onReply(reply)
    }

    fun send(text: String) {
        val message = text.trim()
        if (message.isEmpty() || busy) return
        value = ""

        matchLocal(message, lang, engine)?.let { show(message, it); return }

        busy = true
        scope.launch {
            val reply = when (aiProvider) {
                AiProvider.GEMINI -> {
                    if (geminiKey.isBlank()) offlineReply(lang)
                    else GeminiClient.ask(geminiKey, message, context, lang)
                }
                AiProvider.OPENAI_COMPATIBLE -> {
                    if (openAiBaseUrl.isBlank() || openAiModel.isBlank()) {
                        DeniaReply(
                            tx(
                                lang,
                                "Add an OpenAI-compatible base URL and model in Settings first~",
                                "Isi base URL dan model OpenAI-compatible dulu di Pengaturan ya~",
                            ),
                            Mood.NEUTRAL,
                        )
                    } else {
                        OpenAiCompatibleClient.ask(
                            baseUrl = openAiBaseUrl,
                            apiKey = openAiApiKey,
                            model = openAiModel,
                            message = message,
                            context = context,
                            lang = lang,
                        )
                    }
                }
            }
            busy = false
            show(message, reply)
        }
    }

    fun answer(yes: Boolean) {
        val target = pending ?: return
        pending = null
        if (yes) {
            onOpenUrl(target)
        } else {
            val reply = DeniaReply(tx(lang, "Okay, I will leave it~", "Oke, nggak jadi ya~"), Mood.NEUTRAL)
            last = last?.copy(denia = reply.text)
            onReply(reply)
        }
    }

    val shape = RoundedCornerShape(24.dp)
    val aiReady = when (aiProvider) {
        AiProvider.GEMINI -> geminiKey.isNotBlank()
        AiProvider.OPENAI_COMPATIBLE -> openAiBaseUrl.isNotBlank() && openAiModel.isNotBlank()
    }
    val connectionLabel = when {
        aiReady && aiProvider == AiProvider.GEMINI -> tx(lang, "Gemini connected", "Gemini terhubung")
        aiReady -> tx(lang, "OpenAI-compatible API ready", "API kompatibel OpenAI siap")
        aiProvider == AiProvider.GEMINI ->
            tx(lang, "Add a Gemini key in Settings for smarter replies", "Isi key Gemini di Pengaturan biar makin pintar")
        else ->
            tx(lang, "Add an OpenAI-compatible base URL and model in Settings", "Isi base URL dan model OpenAI-compatible di Pengaturan")
    }
    Column(
        modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = bottomPadding)
            .shadow(20.dp, shape)
            .glassStrong(shape)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painterResource(R.drawable.face_happy),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(28.dp).clip(CircleShape).border(2.dp, Palette.Pink.copy(alpha = 0.6f), CircleShape),
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    tx(lang, "Talk to Denia", "Ngobrol sama Denia"),
                    color = Palette.Ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    connectionLabel,
                    color = Palette.InkMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            RoundIconButton(
                Icons.Filled.Close,
                contentDescription = tx(lang, "Close chat", "Tutup obrolan"),
                onClick = onClose,
                size = 28.dp,
                iconSize = 14.dp,
                tint = Palette.Ink,
                background = Palette.Ink.copy(alpha = 0.1f),
            )
        }

        Spacer(Modifier.height(8.dp))

        val exchange = last
        if (exchange == null) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                deniaSuggestions(lang).forEach { s ->
                    Text(
                        s,
                        color = Palette.Ink,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Palette.Ink.copy(alpha = 0.1f))
                            .press { send(s) }
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }
        } else {
            Row {
                Text(tx(lang, "You:", "Kamu:"), color = Palette.Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, lineHeight = 18.sp)
                Spacer(Modifier.width(4.dp))
                Text(exchange.you, color = Palette.InkMuted, fontSize = 13.sp, lineHeight = 18.sp)
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Top) {
                Text("Denia:", color = Palette.Pink, fontSize = 13.sp, fontWeight = FontWeight.Bold, lineHeight = 18.sp)
                Spacer(Modifier.width(4.dp))
                Text(exchange.denia, color = Palette.Ink, fontSize = 13.sp, lineHeight = 18.sp)
            }
            if (pending != null) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceButton(tx(lang, "No", "Tidak"), primary = false, modifier = Modifier.weight(1f)) { answer(false) }
                    ChoiceButton(tx(lang, "Yes, open", "Ya, buka"), primary = true, modifier = Modifier.weight(1f)) { answer(true) }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Palette.Ink.copy(alpha = 0.1f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                if (value.isEmpty()) {
                    Text(tx(lang, "Ask or command Denia...", "Tanya atau suruh Denia..."), color = Palette.InkMuted, fontSize = 14.sp, maxLines = 1)
                }
                BasicTextField(
                    value = value,
                    onValueChange = { value = it },
                    enabled = !busy,
                    singleLine = true,
                    textStyle = TextStyle(color = Palette.Ink, fontSize = 14.sp),
                    cursorBrush = SolidColor(Palette.Pink),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send(value) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            val canSend = !busy && value.isNotBlank()
            Box(
                Modifier
                    .size(40.dp)
                    .alpha(if (canSend || busy) 1f else 0.5f)
                    .shadow(8.dp, RoundedCornerShape(16.dp), ambientColor = Palette.Pink, spotColor = Palette.Pink)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(Palette.Pink, Palette.Lavender)))
                    .press(enabled = canSend) { send(value) },
                contentAlignment = Alignment.Center,
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Palette.OnAccent,
                    )
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = tx(lang, "Send", "Kirim"),
                        tint = Palette.OnAccent,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ChoiceButton(label: String, primary: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (primary) Palette.Pink else Palette.Ink.copy(alpha = 0.1f))
            .press(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (primary) Palette.OnAccent else Palette.Ink,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
