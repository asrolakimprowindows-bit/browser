package com.multex.browser.denia

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
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
 *
 * KEY BEHAVIOR CHANGES:
 *  - When Denia has a pending action and the user types "yes / ya / iya / buka / ok / open it"
 *    (see isYesAnswer / isNoAnswer), the free-text is treated as CONFIRMATION and executes the
 *    pending action instead of being shipped to Google as a query.
 *  - SEARCH_AND_OPEN_OFFICIAL pending actions actually search the web with Denia's own
 *    DuckDuckGo HTML fetcher (uses the user's internet, no API key) and open the top real
 *    result in a new tab.
 *  - The chat panel now materializes with a spring/scale animation anchored at the source
 *    (bottom-center by default, where Denia lives). Closing plays the reverse (panel collapse
 *    -> "absorbed" back toward Denia).
 */
@Composable
fun DeniaChatBar(
    lang: Lang,
    engine: SearchEngine,
    aiProvider: AiProvider,
    openRouterKey: String,
    openRouterModel: String,
    openAiBaseUrl: String,
    openAiApiKey: String,
    openAiModel: String,
    context: String,
    /** Bottom-center anchor point in local coordinates (Denia's chibi position). Optional. */
    anchor: Offset? = null,
    /** Bumped when the parent wants us to play the "collapse into Denia" animation before closing. */
    closeSignal: Int = 0,
    onReply: (DeniaReply) -> Unit,
    onOpenUrl: (PendingOpen) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 96.dp,
) {
    var value by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var last by remember { mutableStateOf<Exchange?>(null) }
    var pending by remember { mutableStateOf<PendingOpen?>(null) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // 0 = fully absorbed into Denia (invisible), 1 = fully expanded panel. Spring-bounce on open.
    val progress = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Enter: quick expand with a small overshoot, then settle.
        progress.animateTo(1.08f, tween(240, easing = FastOutSlowInEasing))
        progress.animateTo(1f, tween(160, easing = LinearOutSlowInEasing))
    }
    LaunchedEffect(closeSignal) {
        if (closeSignal <= 0 || closing) return@LaunchedEffect
        closing = true
        // Exit: content fades, panel collapses back toward Denia.
        progress.animateTo(0f, tween(260, easing = FastOutSlowInEasing))
        onClose()
    }

    fun requestClose() {
        if (closing) return
        closing = true
        scope.launch {
            progress.animateTo(0f, tween(260, easing = FastOutSlowInEasing))
            onClose()
        }
    }

    fun show(you: String, reply: DeniaReply) {
        last = Exchange(you, reply.text)
        pending = reply.pending
        // A pending open waits for Yes / No; everything else fires immediately.
        if (reply.pending == null) onReply(reply)
    }

    /**
     * Confirm/reject the current pending action. `text` is the raw user input (used only for the
     * bubble echo — it is NEVER shipped as a search query).
     */
    fun confirmPending(yes: Boolean, echo: String) {
        val target = pending ?: return
        pending = null
        if (!yes) {
            val reply = DeniaReply(tx(lang, "Okay, I will leave it~", "Oke, nggak jadi ya~"), Mood.NEUTRAL)
            last = Exchange(echo, reply.text)
            onReply(reply)
            return
        }
        when (target.type) {
            PendingActionType.OPEN_URL -> {
                last = Exchange(echo, tx(lang, "Opening ${target.label}~", "Buka ${target.label}~"))
                onOpenUrl(target)
            }
            PendingActionType.SEARCH_AND_OPEN_OFFICIAL -> {
                busy = true
                last = Exchange(echo, tx(lang, "Searching for ${target.target}...", "Nyari ${target.target}..."))
                scope.launch {
                    val hit = deniaWebSearch("${target.target} official site")
                    busy = false
                    if (hit != null) {
                        val label = target.target
                        last = Exchange(echo, tx(lang, "Found $label — opening ${hit.host}~", "Ketemu $label — buka ${hit.host}~"))
                        onOpenUrl(PendingOpen(hit.url, label, PendingActionType.OPEN_URL, target = label))
                    } else {
                        // Web search failed (no internet / blocked). Fall back to the search-engine page,
                        // but do NOT ship the confirmation text — ship the ORIGINAL target as the query.
                        last = Exchange(
                            echo,
                            tx(
                                lang,
                                "I couldn't reach the web. Opening the search page for ${target.target}~",
                                "Nggak bisa ngakses web. Buka halaman pencarian ${target.target}~",
                            ),
                        )
                        onOpenUrl(PendingOpen(target.url, target.target, PendingActionType.OPEN_URL, target = target.target))
                    }
                }
            }
        }
    }

    fun send(text: String) {
        val message = text.trim()
        if (message.isEmpty() || busy) return
        value = ""

        // If we are waiting for a yes/no, interpret short conversational answers as CONFIRMATION
        // instead of shipping them off to Google.
        if (pending != null) {
            if (isYesAnswer(message)) {
                confirmPending(true, message)
                return
            }
            if (isNoAnswer(message)) {
                confirmPending(false, message)
                return
            }
            // Anything else drops the pending state and is processed as a new query.
            pending = null
        }

        matchLocal(message, lang, engine)?.let { show(message, it); return }

        busy = true
        scope.launch {
            val reply = when (aiProvider) {
                AiProvider.OPENROUTER -> {
                    if (openRouterKey.isBlank()) offlineReply(lang)
                    else OpenRouterClient.ask(openRouterKey, openRouterModel, message, context, lang)
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

    val shape = RoundedCornerShape(24.dp)
    val aiReady = when (aiProvider) {
        AiProvider.OPENROUTER -> openRouterKey.isNotBlank()
        AiProvider.OPENAI_COMPATIBLE -> openAiBaseUrl.isNotBlank() && openAiModel.isNotBlank()
    }
    val connectionLabel = when {
        aiReady && aiProvider == AiProvider.OPENROUTER -> tx(lang, "OpenRouter connected", "OpenRouter terhubung")
        aiReady -> tx(lang, "OpenAI-compatible API ready", "API kompatibel OpenAI siap")
        aiProvider == AiProvider.OPENROUTER ->
            tx(lang, "Add an OpenRouter key in Settings for smarter replies", "Isi key OpenRouter di Pengaturan biar makin pintar")
        else ->
            tx(lang, "Add an OpenAI-compatible base URL and model in Settings", "Isi base URL dan model OpenAI-compatible di Pengaturan")
    }

    // Animation: scale + alpha, transform-origin anchored at Denia's position (or bottom-center).
    val p = progress.value
    val scale = 0.55f + 0.45f * p.coerceAtMost(1.2f)
    val panelAlpha = p.coerceIn(0f, 1f)
    val contentAlpha = ((p - 0.35f) / 0.65f).coerceIn(0f, 1f)

    Column(
        modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = bottomPadding)
            .graphicsLayer {
                // Anchor to Denia when we have her position, else bottom-center of the panel.
                val a = anchor
                if (a != null) {
                    val pivotX = (a.x / size.width).coerceIn(0f, 1f)
                    val pivotY = (a.y / size.height).coerceIn(0f, 1f)
                    transformOrigin = TransformOrigin(pivotX, pivotY)
                } else {
                    transformOrigin = TransformOrigin(0.5f, 1f)
                }
                scaleX = scale
                scaleY = scale
                alpha = panelAlpha
            }
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
                onClick = { requestClose() },
                size = 28.dp,
                iconSize = 14.dp,
                tint = Palette.Ink,
                background = Palette.Ink.copy(alpha = 0.1f),
            )
        }

        Spacer(Modifier.height(8.dp))

        Column(Modifier.graphicsLayer { alpha = contentAlpha }) {
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
                        ChoiceButton(tx(lang, "No", "Tidak"), primary = false, modifier = Modifier.weight(1f)) {
                            confirmPending(false, tx(lang, "No", "Tidak"))
                        }
                        ChoiceButton(tx(lang, "Yes, open", "Ya, buka"), primary = true, modifier = Modifier.weight(1f)) {
                            confirmPending(true, tx(lang, "Yes, open", "Ya, buka"))
                        }
                    }
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
                // Unfocused until the user taps: tapping this field is the only thing that
                // brings up the soft keyboard.
                BasicTextField(
                    value = value,
                    onValueChange = { value = it },
                    enabled = !busy,
                    singleLine = true,
                    textStyle = TextStyle(color = Palette.Ink, fontSize = 14.sp),
                    cursorBrush = SolidColor(Palette.Pink),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send(value) }),
                    modifier = Modifier.fillMaxWidth(),
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
