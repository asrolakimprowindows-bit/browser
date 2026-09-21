package com.multex.browser.denia

/*
 * Jetpack Compose port of components/denia/denia-companion.tsx.
 *
 * Interactions (same as the web preview):
 *  - tap         she says something, and the chat bar opens
 *  - drag        pick her up and drop her anywhere
 *  - double-tap  direct mode: tap any spot and she walks there
 */

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.multex.browser.Lang
import com.multex.browser.Palette
import com.multex.browser.R
import com.multex.browser.RoundIconButton
import com.multex.browser.glassStrong
import com.multex.browser.tx
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.roundToInt

enum class Pose { FRONT, SIDE, BACK, SIT }

enum class Mood(val wire: String) {
    HAPPY("happy"),
    NEUTRAL("neutral"),
    POUT("pout");

    companion object {
        fun fromWire(id: String?): Mood = entries.firstOrNull { it.wire == id } ?: HAPPY
    }
}

/** What the bubble shows. */
data class Line(val text: String, val mood: Mood = Mood.HAPPY)

/** A request from the browser for Denia to say something. [force] means "even if she is not chatty". */
data class Cue(val id: Int, val text: String, val mood: Mood = Mood.HAPPY, val force: Boolean = false)

private data class Marker(val at: Offset, val id: Int)

private enum class BubbleAlign { START, CENTER, END }

private fun tapLines(lang: Lang) = listOf(
    Line(tx(lang, "Hehe, need something?", "Hehe, butuh sesuatu?")),
    Line(tx(lang, "Denia, reporting for duty~", "Denia siap melayani~")),
    Line(tx(lang, "Hmph! Stop poking me.", "Hmph! Jangan colek-colek."), Mood.POUT),
    Line(tx(lang, "Want me to open a new tab?", "Mau aku bukain tab baru?"), Mood.NEUTRAL),
    Line(tx(lang, "You have been scrolling a while. Water break?", "Udah lama scroll nih. Minum dulu?"), Mood.NEUTRAL),
    Line(tx(lang, "Double-tap me and I will go wherever you point!", "Ketuk dua kali, aku ke mana pun kamu tunjuk!")),
)

private fun arriveLines(lang: Lang) = listOf(
    Line(tx(lang, "Here I am!", "Aku di sini!")),
    Line(tx(lang, "Made it~", "Sampai~")),
    Line(tx(lang, "Is this the spot?", "Di sini tempatnya?"), Mood.NEUTRAL),
    Line(tx(lang, "Phew, that was far.", "Huft, jauh juga."), Mood.POUT),
)

private fun dropLines(lang: Lang) = listOf(
    Line(tx(lang, "Wheee!", "Wiii!")),
    Line(tx(lang, "Careful, I am fragile!", "Pelan-pelan, aku rapuh!"), Mood.POUT),
    Line(tx(lang, "New spot, new view~", "Tempat baru, pemandangan baru~")),
)

private fun poseRes(pose: Pose) = when (pose) {
    Pose.FRONT -> R.drawable.denia_front
    Pose.SIDE -> R.drawable.denia_side
    Pose.BACK -> R.drawable.denia_back
    Pose.SIT -> R.drawable.denia_sit
}

fun faceRes(mood: Mood) = when (mood) {
    Mood.HAPPY -> R.drawable.face_happy
    Mood.NEUTRAL -> R.drawable.face_neutral
    Mood.POUT -> R.drawable.face_pout
}

/** Places the speech bubble above (or below) the sprite, kept inside the screen like `align` in the web build. */
private fun Modifier.bubblePlacement(below: Boolean, align: BubbleAlign, gapPx: Int): Modifier =
    layout { measurable, constraints ->
        val parentW = constraints.maxWidth
        val parentH = constraints.maxHeight
        // Measure with no upper bound: the bubble limits its own width (max 190dp).
        val placeable = measurable.measure(Constraints(0, Constraints.Infinity, 0, Constraints.Infinity))
        layout(0, 0) {
            val x = when (align) {
                BubbleAlign.START -> 0
                BubbleAlign.END -> parentW - placeable.width
                BubbleAlign.CENTER -> (parentW - placeable.width) / 2
            }
            val y = if (below) parentH + gapPx else -placeable.height - gapPx
            placeable.place(x, y)
        }
    }

@Composable
fun DeniaCompanion(
    modifier: Modifier = Modifier,
    height: Dp = 136.dp,
    petRes: Int? = null,
    chatty: Boolean = true,
    cue: Cue? = null,
    scrollSignal: Int = 0,
    directMode: Boolean,
    onDirectModeChange: (Boolean) -> Unit,
    lang: Lang = Lang.EN,
    onTap: () -> Unit = {},
) {
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnDirect by rememberUpdatedState(onDirectModeChange)
    val currentDirectMode by rememberUpdatedState(directMode)

    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val hPx = with(density) { height.toPx() }
        val wPx = hPx * 0.78f
        val topPx = with(density) { 28.dp.toPx() }
        val reservePx = with(density) { 64.dp.toPx() }
        val maxX = (constraints.maxWidth - wPx).coerceAtLeast(0f)
        val maxY = (constraints.maxHeight - hPx - reservePx).coerceAtLeast(topPx)
        val boundsW = constraints.maxWidth.toFloat()

        val scope = rememberCoroutineScope()
        val offset = remember {
            Animatable(
                Offset(maxX - with(density) { 10.dp.toPx() }, maxY - with(density) { 40.dp.toPx() }),
                Offset.VectorConverter,
            )
        }
        var pose by remember { mutableStateOf(Pose.FRONT) }
        var facingRight by remember { mutableStateOf(false) }
        var walking by remember { mutableStateOf(false) }
        var dragging by remember { mutableStateOf(false) }
        var bubble by remember { mutableStateOf<Line?>(null) }
        var bubbleJob by remember { mutableStateOf<Job?>(null) }
        var idleJob by remember { mutableStateOf<Job?>(null) }
        var marker by remember { mutableStateOf<Marker?>(null) }
        var markerSeq by remember { mutableIntStateOf(0) }

        fun clamp(p: Offset) = Offset(p.x.coerceIn(0f, maxX), p.y.coerceIn(topPx, maxY))

        fun say(line: Line, ms: Long = 3200) {
            bubble = line
            bubbleJob?.cancel()
            bubbleJob = scope.launch { delay(ms); bubble = null }
        }

        fun poke() {
            if (pose == Pose.SIT) pose = Pose.FRONT
            idleJob?.cancel()
            idleJob = scope.launch {
                delay(18_000)
                pose = Pose.SIT
                if (chatty) say(Line(tx(lang, "Zzz... just resting my eyes.", "Zzz... istirahat mata dulu."), Mood.NEUTRAL), 2600)
            }
        }

        fun walkTo(target: Offset) {
            val to = clamp(target)
            val dist = hypot(to.x - offset.value.x, to.y - offset.value.y)
            if (dist < 4f) return
            facingRight = to.x > offset.value.x
            pose = Pose.SIDE
            walking = true
            scope.launch {
                offset.animateTo(to, tween((dist * 6).toInt().coerceIn(450, 2600), easing = FastOutSlowInEasing))
                walking = false
                pose = Pose.FRONT
                say(arriveLines(lang).random())
                poke()
            }
        }

        // Keep her on screen when the usable area changes (keyboard, rotation).
        LaunchedEffect(maxX, maxY) { offset.snapTo(clamp(offset.value)) }
        LaunchedEffect(Unit) { poke() }
        LaunchedEffect(cue) { cue?.let { if (chatty || it.force) say(Line(it.text, it.mood)) } }
        LaunchedEffect(scrollSignal) {
            if (scrollSignal == 0 || walking) return@LaunchedEffect
            pose = Pose.BACK
            poke()
            delay(1200)
            if (pose == Pose.BACK) pose = Pose.FRONT
        }

        if (directMode) {
            // Full-screen catcher: the next tap anywhere is her destination.
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(wPx, hPx, maxX, maxY) {
                        detectTapGestures { tap ->
                            markerSeq += 1
                            marker = Marker(tap, markerSeq)
                            walkTo(Offset(tap.x - wPx / 2, tap.y - hPx))
                            poke()
                        }
                    },
            )
            Row(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 44.dp)
                    .shadow(10.dp, CircleShape)
                    .glassStrong(999.dp)
                    .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.GpsFixed, contentDescription = null, tint = Palette.Pink, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    tx(lang, "Direct mode · tap anywhere", "Mode arah · ketuk di mana saja"),
                    color = Palette.Ink,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(8.dp))
                RoundIconButton(
                    Icons.Filled.Close,
                    contentDescription = tx(lang, "Exit direct mode", "Keluar mode arah"),
                    onClick = { currentOnDirect(false) },
                    size = 24.dp,
                    iconSize = 12.dp,
                    tint = Palette.Ink,
                    background = Palette.Ink.copy(alpha = 0.1f),
                )
            }
            marker?.let { m ->
                key(m.id) {
                    val progress = remember { Animatable(0f) }
                    LaunchedEffect(Unit) {
                        progress.animateTo(1f, tween(520))
                        marker = null
                    }
                    Box(
                        Modifier
                            .offset { IntOffset((m.at.x - 20.dp.toPx()).roundToInt(), (m.at.y - 20.dp.toPx()).roundToInt()) }
                            .size(40.dp)
                            .graphicsLayer {
                                val s = 0.3f + 1.3f * progress.value
                                scaleX = s
                                scaleY = s
                                alpha = 0.9f * (1f - progress.value)
                            }
                            .border(2.dp, Palette.Pink, CircleShape),
                    )
                }
            }
        }

        val motion = rememberInfiniteTransition(label = "denia")
        val floatY by motion.animateFloat(
            initialValue = 0f,
            targetValue = if (walking) -7f else -6f,
            animationSpec = infiniteRepeatable(
                tween(if (walking) 190 else 1600, easing = FastOutSlowInEasing),
                RepeatMode.Reverse,
            ),
            label = "floatY",
        )
        val sway by motion.animateFloat(
            initialValue = -1.5f,
            targetValue = 1.5f,
            animationSpec = infiniteRepeatable(tween(190), RepeatMode.Reverse),
            label = "sway",
        )
        val hopY by motion.animateFloat(
            initialValue = 0f,
            targetValue = -9f,
            animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "hopY",
        )

        val edge = with(density) { 70.dp.toPx() }
        val bubbleAlign = when {
            offset.value.x < edge -> BubbleAlign.START
            offset.value.x + wPx > boundsW - edge -> BubbleAlign.END
            else -> BubbleAlign.CENTER
        }
        val bubbleBelow = offset.value.y < with(density) { 90.dp.toPx() }

        Box(
            Modifier
                .offset { IntOffset(offset.value.x.roundToInt(), offset.value.y.roundToInt()) }
                .size(with(density) { wPx.toDp() }, height)
                .pointerInput(lang, chatty) {
                    detectTapGestures(
                        onTap = {
                            say(tapLines(lang).random())
                            poke()
                            currentOnTap()
                        },
                        onDoubleTap = {
                            val next = !currentDirectMode
                            currentOnDirect(next)
                            say(
                                if (next) Line(tx(lang, "Tap anywhere and I will run there!", "Ketuk di mana saja, aku lari ke sana!"))
                                else Line(tx(lang, "Okay, staying put~", "Oke, aku diam di sini~"), Mood.NEUTRAL),
                            )
                            poke()
                        },
                    )
                }
                .pointerInput(maxX, maxY, lang, chatty) {
                    detectDragGestures(
                        onDragStart = { dragging = true; walking = false; pose = Pose.FRONT },
                        onDrag = { change, delta ->
                            change.consume()
                            scope.launch { offset.snapTo(clamp(offset.value + delta)) }
                        },
                        onDragEnd = {
                            dragging = false
                            if (chatty) say(dropLines(lang).random(), 2200)
                            poke()
                        },
                        onDragCancel = { dragging = false },
                    )
                },
        ) {
            AnimatedVisibility(
                visible = bubble != null,
                enter = fadeIn() + scaleIn(initialScale = 0.9f),
                exit = fadeOut(),
                modifier = Modifier.bubblePlacement(bubbleBelow, bubbleAlign, with(density) { 8.dp.roundToPx() }),
            ) {
                bubble?.let { line ->
                    Row(
                        Modifier
                            .widthIn(max = 190.dp)
                            .shadow(10.dp, RoundedCornerShape(16.dp))
                            .glassStrong(16.dp)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(
                            painterResource(faceRes(line.mood)),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(28.dp).clip(CircleShape).border(2.dp, Palette.Pink, CircleShape),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            line.text,
                            color = Palette.Ink,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 17.sp,
                        )
                    }
                }
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .offset { IntOffset(0, if (pose == Pose.SIT || dragging) 0 else floatY.dp.roundToPx()) }
                    .scale(if (dragging) 1.06f else 1f)
                    .graphicsLayer { rotationZ = if (dragging) -4f else if (walking) sway else 0f },
            ) {
                petRes?.let {
                    Image(
                        painterResource(it),
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .offset(x = -(height * 0.2f))
                            .offset { IntOffset(0, hopY.dp.roundToPx()) }
                            .height(height * 0.38f),
                    )
                }
                Image(
                    painterResource(poseRes(pose)),
                    contentDescription = tx(lang, "Denia, your companion", "Denia, teman browsingmu"),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .height(height)
                        .graphicsLayer { scaleX = if (pose == Pose.SIDE && facingRight) -1f else 1f },
                )
            }
        }
    }
}
