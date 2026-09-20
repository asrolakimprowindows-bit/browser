package com.multex.browser.denia

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.multex.browser.R
import com.multex.browser.SearchEngine

private data class Exchange(val you: String, val denia: DeniaReply)

/**
 * Chat bar that sits above the toolbar. Commands are matched on-device; when Denia proposes
 * opening a site she waits for a No / Yes answer before anything happens.
 */
@Composable
fun DeniaChatBar(
    lang: Lang,
    engine: SearchEngine,
    onReply: (DeniaReply) -> Unit,
    onOpenUrl: (PendingOpen) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var value by remember { mutableStateOf("") }
    var last by remember { mutableStateOf<Exchange?>(null) }
    var pending by remember { mutableStateOf<PendingOpen?>(null) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { focus.requestFocus() }

    fun send(text: String) {
        val message = text.trim()
        if (message.isEmpty()) return
        value = ""
        val reply = deniaReply(message, lang, engine)
        last = Exchange(message, reply)
        pending = reply.pending
        if (reply.pending == null) onReply(reply)
    }

    fun answer(yes: Boolean) {
        val target = pending ?: return
        pending = null
        if (yes) {
            onOpenUrl(target)
            last = last?.copy(denia = DeniaReply(tx(lang, "Opening ${target.label} in a new tab~", "Buka ${target.label} di tab baru~")))
        } else {
            val reply = DeniaReply(tx(lang, "Okay, I will leave it~", "Oke, nggak jadi ya~"), Mood.NEUTRAL)
            last = last?.copy(denia = reply)
            onReply(reply)
        }
    }

    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .glass(24.dp, strong = true)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painterResource(R.drawable.face_happy),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(28.dp).clip(CircleShape).border(2.dp, DeniaPalette.Pink, CircleShape),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                tx(lang, "Talk to Denia", "Ngobrol sama Denia"),
                color = DeniaPalette.Ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Close, contentDescription = tx(lang, "Close chat", "Tutup obrolan"), tint = DeniaPalette.Ink, modifier = Modifier.size(16.dp))
            }
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
                        color = DeniaPalette.Ink,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(DeniaPalette.Ink.copy(alpha = 0.1f))
                            .clickable { send(s) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        } else {
            Text(
                buildString { append(tx(lang, "You: ", "Kamu: ")); append(exchange.you) },
                color = DeniaPalette.InkMuted,
                fontSize = 13.sp,
                lineHeight = 17.sp,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.Top) {
                Text("Denia: ", color = DeniaPalette.Pink, fontSize = 13.sp, fontWeight = FontWeight.Bold, lineHeight = 17.sp)
                Text(exchange.denia.text, color = DeniaPalette.Ink, fontSize = 13.sp, lineHeight = 17.sp)
            }
            pending?.let {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceButton(tx(lang, "No", "Tidak"), primary = false, modifier = Modifier.weight(1f)) { answer(false) }
                    ChoiceButton(tx(lang, "Yes, open", "Ya, buka"), primary = true, modifier = Modifier.weight(1f)) { answer(true) }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(DeniaPalette.Ink.copy(alpha = 0.1f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                if (value.isEmpty()) {
                    Text(tx(lang, "Ask or command Denia...", "Tanya atau suruh Denia..."), color = DeniaPalette.InkMuted, fontSize = 14.sp, maxLines = 1)
                }
                BasicTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    textStyle = TextStyle(color = DeniaPalette.Ink, fontSize = 14.sp),
                    cursorBrush = SolidColor(DeniaPalette.Pink),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send(value) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            Box(
                Modifier
                    .size(40.dp)
                    .background(
                        Brush.linearGradient(listOf(DeniaPalette.Pink, DeniaPalette.Lavender)),
                        RoundedCornerShape(16.dp),
                    )
                    .clickable(enabled = value.isNotBlank()) { send(value) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = tx(lang, "Send", "Kirim"),
                    tint = DeniaPalette.Midnight,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun ChoiceButton(label: String, primary: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (primary) DeniaPalette.Pink else DeniaPalette.Ink.copy(alpha = 0.1f))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (primary) DeniaPalette.Midnight else DeniaPalette.Ink,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
