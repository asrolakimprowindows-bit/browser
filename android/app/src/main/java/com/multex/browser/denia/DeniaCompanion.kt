package com.multex.browser.denia

/*
 * Drop-in Jetpack Compose port of the web companion in components/denia/denia-companion.tsx.
 *
 * Setup:
 *  1. Copy the PNGs from public/denia into app/src/main/res/drawable (names already match R.drawable).
 *  2. Place <DeniaCompanion/> as the LAST child of the Box that wraps your WebView + toolbar so it
 *     draws above everything.
 *  3. Feed `cue` from your browser state (e.g. "Opening pixiv.net~") and Denia will speak it.
 *
 * Interactions: tap = speak, drag = move, double-tap = direct mode (tap anywhere and she walks there).
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
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.multex.browser.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.roundToInt

enum class ThemeId(val id: String) { MIDNIGHT("midnight"), SAKURA("sakura") }

/*
 * Colors are Compose state so switching theme recomposes everything that reads the palette,
 * without threading a theme object through every composable.
 */
object DeniaPalette {
    var Ink by mutableStateOf(Color(0xFFF4F1FF))
    var InkMuted by mutableStateOf(Color(0xFFA3A1C6))
    var Pink by mutableStateOf(Color(0xFFF79AC8))
    var Lavender by mutableStateOf(Color(0xFFB9A9FF))
    var Sky by mutableStateOf(Color(0xFF8AA8FF))
    var Midnight by mutableStateOf(Color(0xFF0A0B1C))
    var MidnightSoft by mutableStateOf(Color(0xFF1B1D45))
    var Surface by mutableStateOf(Color(0xFF15173A))
    var GlassTint by mutableStateOf(Color(0x0FFFFFFF))
    var GlassStrong by mutableStateOf(Color(0xE01A1B36))
    var GlassBorder by mutableStateOf(Color(0x1AFFFFFF))

    fun applyTheme(theme: ThemeId) {
        if (theme == ThemeId.SAKURA) {
            Ink = Color(0xFF2B2140); InkMuted = Color(0xFF7D7297); Pink = Color(0xFFE86FAE)
            Lavender = Color(0xFF7F6BD6); Sky = Color(0xFF5B82E6); Midnight = Color(0xFFFFF3F9)
            MidnightSoft = Color(0xFFFFD6EA); Surface = Color(0xFFFFF8FC); GlassTint = Color(0x8CFFFFFF)
            GlassStrong = Color(0xF0FFFAFD); GlassBorder = Color(0xD9FFFFFF)
        } else {
            Ink = Color(0xFFF4F1FF); InkMuted = Color(0xFFA3A1C6); Pink = Color(0xFFF79AC8)
            Lavender = Color(0xFFB9A9FF); Sky = Color(0xFF8AA8FF); Midnight = Color(0xFF0A0B1C)
            MidnightSoft = Color(0xFF1B1D45); Surface = Color(0xFF15173A); GlassTint = Color(0x0FFFFFFF)
            GlassStrong = Color(0xE01A1B36); GlassBorder = Color(0x1AFFFFFF)
        }
    }
}

fun Modifier.glass(radius: Dp = 24.dp, strong: Boolean = false): Modifier =
    clip(RoundedCornerShape(radius))
        .background(if (strong) DeniaPalette.GlassStrong else DeniaPalette.GlassTint)
        .border(1.dp, DeniaPalette.GlassBorder, RoundedCornerShape(radius))

enum class Pose { FRONT, SIDE, BACK, SIT }
enum class Mood { HAPPY, NEUTRAL, POUT }
data class Line(val text: String, val mood: Mood = Mood.HAPPY)

private fun tapLines(lang: Lang) = listOf(
    Line(tx(lang, "Hehe, need something?", "Hehe, butuh sesuatu?")),
    Line(tx(lang, "Denia, reporting for duty~", "Denia siap melayani~")),
    Line(tx(lang, "Hmph! Stop poking me.", "Hmph! Jangan colek-colek."), Mood.POUT),
    Line(tx(lang, "Hold me to chat with me!", "Tahan aku kalau mau ngobrol!"), Mood.NEUTRAL),
    Line(tx(lang, "Double-tap me and I will go wherever you point!", "Ketuk dua kali, aku ke mana pun kamu tunjuk!")),
)
private fun arriveLines(lang: Lang) = listOf(
    Line(tx(lang, "Here I am!", "Aku di sini!")),
    Line(tx(lang, "Made it~", "Sampai~")),
    Line(tx(lang, "Is this the spot?", "Di sini tempatnya?"), Mood.NEUTRAL),
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

private fun faceRes(mood: Mood) = when (mood) {
    Mood.HAPPY -> R.drawable.face_happy
    Mood.NEUTRAL -> R.drawable.face_neutral
    Mood.POUT -> R.drawable.face_pout
}

@Composable
fun DeniaCompanion(
    modifier: Modifier = Modifier,
    height: Dp = 136.dp,
    petRes: Int? = null,
    chatty: Boolean = true,
    cue: Line? = null,
    scrollSignal: Int = 0,
    directMode: Boolean,
    onDirectModeChange: (Boolean) -> Unit,
    lang: Lang = Lang.EN,
    onOpenChat: () -> Unit = {},
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val hPx = with(density) { height.toPx() }
        val wPx = hPx * 0.78f
        val maxX = constraints.maxWidth - wPx
        val maxY = constraints.maxHeight - hPx - with(density) { 64.dp.toPx() }

        val scope = rememberCoroutineScope()
        val offset = remember { Animatable(Offset(maxX - 10f, maxY - 40f), Offset.VectorConverter) }
        var pose by remember { mutableStateOf(Pose.FRONT) }
        var facingRight by remember { mutableStateOf(false) }
        var walking by remember { mutableStateOf(false) }
        var dragging by remember { mutableStateOf(false) }
        var bubble by remember { mutableStateOf<Line?>(null) }
        var bubbleJob by remember { mutableStateOf<Job?>(null) }
        var idleJob by remember { mutableStateOf<Job?>(null) }

        fun clamp(p: Offset) = Offset(p.x.coerceIn(0f, maxX), p.y.coerceIn(28f, maxY))

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

        LaunchedEffect(Unit) { poke() }
        LaunchedEffect(cue) { cue?.let { if (chatty) say(it) } }
        LaunchedEffect(scrollSignal) {
            if (scrollSignal == 0 || walking) return@LaunchedEffect
            pose = Pose.BACK
            delay(1200)
            if (pose == Pose.BACK) pose = Pose.FRONT
        }

        if (directMode) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { tap -> walkTo(Offset(tap.x - wPx / 2, tap.y - hPx)); poke() }
                    }
            )
            Row(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 44.dp)
                    .glass(999.dp, strong = true)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(tx(lang, "Direct mode · tap anywhere", "Mode arah · ketuk di mana saja"), color = DeniaPalette.Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text(
                    tx(lang, "Done", "Selesai"),
                    color = DeniaPalette.Pink,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.pointerInput(Unit) { detectTapGestures { onDirectModeChange(false) } },
                )
            }
        }

        val bob by rememberInfiniteTransition(label = "bob").animateFloat(
            initialValue = 0f,
            targetValue = if (walking) -7f else -6f,
            animationSpec = infiniteRepeatable(tween(if (walking) 190 else 1600), RepeatMode.Reverse),
            label = "bobY",
        )

        Box(
            Modifier
                .offset { IntOffset(offset.value.x.roundToInt(), offset.value.y.roundToInt()) }
                .size(with(density) { wPx.toDp() }, height)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { say(tapLines(lang).random()); poke() },
                        onLongPress = { onOpenChat(); poke() },
                        onDoubleTap = {
                            val next = !directMode
                            onDirectModeChange(next)
                            say(
                                if (next) Line(tx(lang, "Tap anywhere and I will run there!", "Ketuk di mana saja, aku lari ke sana!"))
                                else Line(tx(lang, "Okay, staying put~", "Oke, aku diam di sini~"), Mood.NEUTRAL),
                            )
                        },
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { dragging = true; walking = false; pose = Pose.FRONT },
                        onDrag = { change, delta ->
                            change.consume()
                            scope.launch { offset.snapTo(clamp(offset.value + delta)) }
                        },
                        onDragEnd = { dragging = false; if (chatty) say(dropLines(lang).random(), 2200); poke() },
                        onDragCancel = { dragging = false },
                    )
                },
        ) {
            AnimatedVisibility(
                visible = bubble != null,
                enter = fadeIn() + scaleIn(initialScale = 0.9f),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter).offset(y = (-48).dp),
            ) {
                bubble?.let { line ->
                    Row(
                        Modifier
                            .widthIn(max = 190.dp)
                            .glass(18.dp, strong = true)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(
                            painterResource(faceRes(line.mood)),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(28.dp).clip(CircleShape).border(2.dp, DeniaPalette.Pink, CircleShape),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(line.text, color = DeniaPalette.Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, lineHeight = 17.sp)
                    }
                }
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .offset(y = if (pose == Pose.SIT || dragging) 0.dp else bob.dp)
                    .scale(if (dragging) 1.06f else 1f)
                    .graphicsLayer { rotationZ = if (dragging) -4f else 0f },
            ) {
                petRes?.let {
                    Image(
                        painterResource(it),
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .offset(x = -(height * 0.2f))
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
