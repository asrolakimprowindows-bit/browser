package com.multex.browser

/*
 * Fullscreen mode ("immersive") with the orb-absorption transition.
 *
 * State machine (owned by BrowserModel, transitions are spam-proof):
 *   NORMAL -> ENTERING -> ORB -> MENU_OPEN -> ORB -> EXITING -> NORMAL
 *
 * ENTERING  Ghost copies of the real address bar and bottom dock fly into the orb:
 *           translate + shrink + swirl, plus a ring of tiny energy particles that spiral
 *           INTO the orb like a black hole accretion disk. The Android system bars are
 *           only hidden AFTER the absorption finishes, so nothing pops mid-animation.
 * ORB       Just the floating orb. Draggable, snaps to the nearest screen edge, and idles
 *           with a slow breathing glow.
 * MENU_OPEN The orb spits the controls back out around itself (back / forward /
 *           address / reload / tabs / exit) with scale + alpha + translation + stagger.
 * EXITING   Laser-style restoration: an energy beam shoots from the orb toward each UI
 *           element's home position (address bar -> dock -> nav) and each element
 *           materializes as its beam lands, like the orb is re-printing the chrome.
 */

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Fullscreen states. ENTERING / EXITING are transient; input is ignored while in them. */
enum class FsState { NORMAL, ENTERING, ORB, MENU_OPEN, EXITING }

/** A single accretion particle spiraling into the orb. */
private data class Particle(
    val startAngle: Float,
    val radius0: Float,
    val size: Float,
    val hue: Float, // 0..1 pick between pink / lavender / sky
    val spin: Float, // radians of extra rotation over its life
)

@Composable
fun ImmersiveOrb(
    lang: Lang,
    tab: Tab,
    blockTrackers: Boolean,
    canForward: Boolean,
    tabCount: Int,
    fs: FsState,
    /** Performance profile: caps how many particles / how often the vortex recomputes. */
    detail: OrbDetail = OrbDetail.BALANCE,
    onAbsorbed: () -> Unit,
    onRestored: () -> Unit,
    onMenuOpenChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onSubmit: (String) -> Unit,
    onReloadOrStop: () -> Unit,
    onTabs: () -> Unit,
    onExit: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val scope = rememberCoroutineScope()

        val orbSize = 56.dp
        val margin = 14.dp
        val gap = 8.dp
        val btnSize = 40.dp
        val panelHeight = 60.dp

        val maxW = with(density) { maxWidth.toPx() }
        val maxH = with(density) { maxHeight.toPx() }
        val orbPx = with(density) { orbSize.toPx() }
        val marginPx = with(density) { margin.toPx() }
        val panelHPx = with(density) { panelHeight.toPx() }

        // Home position: docked on the right edge, slightly above center.
        val homeX = (maxW - orbPx - marginPx).coerceAtLeast(marginPx)
        val homeY = (maxH * 0.42f).coerceIn(marginPx, (maxH - orbPx - marginPx).coerceAtLeast(marginPx))

        val x = remember { Animatable(homeX) }
        val y = remember { Animatable(homeY) }
        var dragging by remember { mutableStateOf(false) }

        // Absorption driver: 1 = chrome fully in place, 0 = everything swallowed by the orb.
        val t = remember { Animatable(1f) }
        // Orb -> menu driver: 0 = just the orb, 1 = menu fully unfolded.
        val menuT = remember { Animatable(0f) }
        // One-shot ripple ring fired when the orb finishes materializing.
        val ring = remember { Animatable(1f) }
        // Exit driver for the laser phase: runs 0 -> 1, independent of `t`.
        val laserT = remember { Animatable(0f) }

        // Real, runtime-measured centers (no hardcoded positions). Measured on UNTRANSFORMED
        // wrapper boxes, so the flight transform never feeds back into the measurement.
        var addressCenter by remember { mutableStateOf<Offset?>(null) }
        var dockCenter by remember { mutableStateOf<Offset?>(null) }
        val itemCenters = remember { arrayOfNulls<Offset>(6) }

        // Performance scaling: particles count per detail mode.
        val particleCount = when (detail) {
            OrbDetail.POWERSAVE -> 6
            OrbDetail.BALANCE -> 14
            OrbDetail.MAX -> 26
        }
        val particles = remember(particleCount) {
            List(particleCount) { i ->
                val frac = i.toFloat() / particleCount
                Particle(
                    startAngle = frac * (2f * Math.PI.toFloat()) + (i * 0.7f) % 1.3f,
                    radius0 = with(density) { (90 + (i % 5) * 26).dp.toPx() },
                    size = with(density) { (2.2f + (i % 3) * 1.6f).dp.toPx() },
                    hue = frac,
                    spin = Math.PI.toFloat() * (1.6f + frac * 2.4f),
                )
            }
        }

        LaunchedEffect(fs) {
            when (fs) {
                FsState.ENTERING -> {
                    x.snapTo(homeX)
                    y.snapTo(homeY)
                    menuT.snapTo(0f)
                    laserT.snapTo(0f)
                    // Accelerate INTO the orb, like gravity pulling the chrome in.
                    t.animateTo(0f, tween(560, easing = FastOutLinearInEasing))
                    ring.snapTo(0f)
                    launch { ring.animateTo(1f, tween(650, easing = FastOutSlowInEasing)) }
                    onAbsorbed()
                }
                FsState.MENU_OPEN -> menuT.animateTo(1f, tween(360, easing = FastOutSlowInEasing))
                FsState.ORB -> menuT.animateTo(0f, tween(280))
                FsState.EXITING -> {
                    menuT.snapTo(0f)
                    // Phase 1: the orb "charges" briefly, then fires lasers at each chrome element.
                    // `t` stays 0 during the laser run so the ghosts are not drawn mid-flight;
                    // each element materializes only after its beam lands (see laserStage below).
                    laserT.animateTo(1f, tween(720, easing = FastOutSlowInEasing))
                    // Phase 2: settle: chrome fades from materialized (0.65) to fully in place (1).
                    t.animateTo(1f, tween(300, easing = LinearOutSlowInEasing))
                    onRestored()
                }
                FsState.NORMAL -> Unit
            }
        }

        // The IME / transient system bars shrink this layer; pull the orb back inside.
        LaunchedEffect(maxW, maxH) {
            launch { x.animateTo(x.value.coerceIn(0f, (maxW - orbPx).coerceAtLeast(0f))) }
            launch { y.animateTo(y.value.coerceIn(0f, (maxH - orbPx).coerceAtLeast(0f))) }
        }

        val dockedRight = x.value + orbPx / 2f > maxW / 2f

        fun dock() {
            scope.launch {
                val targetX = if (dockedRight) homeX else marginPx
                val targetY = y.value.coerceIn(marginPx, (maxH - orbPx - marginPx).coerceAtLeast(marginPx))
                launch { x.animateTo(targetX, spring(dampingRatio = 0.68f, stiffness = 420f)) }
                launch { y.animateTo(targetY, spring(dampingRatio = 0.68f, stiffness = 420f)) }
            }
        }

        /** Translate + shrink + swirl an element (centered at [center]) into the orb as t goes 1 -> 0. */
        fun absorbed(center: Offset?, clockwise: Boolean): Modifier = Modifier.graphicsLayer {
            val k = t.value
            val cx = x.value + orbPx / 2f
            val cy = y.value + orbPx / 2f
            val c = center
            if (c != null) {
                translationX = (cx - c.x) * (1f - k)
                translationY = (cy - c.y) * (1f - k)
            }
            val s = 0.18f + 0.82f * k
            scaleX = s
            scaleY = s
            alpha = k
            rotationZ = (1f - k) * (if (clockwise) 50f else -50f)
        }

        // ---------- Ghost chrome being absorbed / released ----------
        // Exact visual copies of the real AddressBar / BottomDock (clicks disabled; the real
        // bars are already gone). They start exactly where the real bars were on screen.
        if (t.value > 0.01f) {
            Column(Modifier.fillMaxWidth().onGloballyPositioned { addressCenter = it.boundsInRoot().center }) {
                Box(Modifier.fillMaxWidth().then(absorbed(addressCenter, clockwise = false))) {
                    AddressBar(
                        tab = tab,
                        blockTrackers = blockTrackers,
                        lang = lang,
                        onHome = {},
                        onSubmit = { _ -> },
                        onReloadOrStop = {},
                    )
                }
            }
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .onGloballyPositioned { dockCenter = it.boundsInRoot().center },
            ) {
                Box(Modifier.fillMaxWidth().then(absorbed(dockCenter, clockwise = true))) {
                    BottomDock(
                        lang = lang,
                        canBack = true,
                        canForward = canForward,
                        tabCount = tabCount,
                        onBack = {},
                        onForward = {},
                        onNewTab = {},
                        onTabs = {},
                        onMenu = {},
                    )
                }
            }
        }

        // ---------- Black-hole accretion: particles spiral into the orb during ENTERING ----------
        // Skipped in POWERSAVE to guarantee 30fps on weak devices; minimal on BALANCE; full vortex on MAX.
        if (fs == FsState.ENTERING && detail != OrbDetail.POWERSAVE) {
            val inT = t.value // 1 -> 0 as we absorb
            val cx = x.value + orbPx / 2f
            val cy = y.value + orbPx / 2f
            Canvas(Modifier.fillMaxSize()) {
                val n = particles.size
                for (i in 0 until n) {
                    val p = particles[i]
                    // Each particle lives on a slightly offset cycle so the vortex never syncs.
                    val life = ((1f - inT) + i * 0.13f) % 1f
                    val ang = p.startAngle + p.spin * life
                    val rad = p.radius0 * (1f - life * 0.92f)
                    val px = cx + cos(ang) * rad
                    val py = cy + sin(ang) * rad * 0.82f // squashed ellipse -> vortex feel
                    val alpha = (sin(life * Math.PI).toFloat()) * 0.9f
                    val color = when {
                        p.hue < 0.34f -> Palette.Pink
                        p.hue < 0.67f -> Palette.Lavender
                        else -> Palette.Sky
                    }
                    drawCircle(
                        color = color.copy(alpha = alpha),
                        radius = p.size * (0.6f + life),
                        center = Offset(px, py),
                    )
                }
            }
        }

        // ---------- Laser restoration: beams shoot from the orb to each chrome element ----------
        // Drawn only during EXITING. Staggered per element: address bar first, then dock.
        if (fs == FsState.EXITING) {
            val lp = laserT.value // 0 -> 1 over the laser phase
            val cx = x.value + orbPx / 2f
            val cy = y.value + orbPx / 2f

            // Per-target laser progress with stagger. Address: 0..0.5, dock: 0.25..0.8.
            fun laserProgress(start: Float, end: Float): Float =
                ((lp - start) / (end - start)).coerceIn(0f, 1f)

            val addrP = laserProgress(0.0f, 0.5f)
            val dockP = laserProgress(0.25f, 0.8f)

            Canvas(Modifier.fillMaxSize()) {
                fun drawBeam(target: Offset?, prog: Float) {
                    if (target == null || prog <= 0f || prog >= 1f) return
                    val end = Offset(
                        cx + (target.x - cx) * prog,
                        cy + (target.y - cy) * prog,
                    )
                    // Core beam
                    drawLine(
                        color = Palette.Pink.copy(alpha = 0.9f),
                        start = Offset(cx, cy),
                        end = end,
                        strokeWidth = 5f * (1f - prog * 0.4f),
                    )
                    // Glow halo
                    drawLine(
                        color = Palette.Lavender.copy(alpha = 0.35f),
                        start = Offset(cx, cy),
                        end = end,
                        strokeWidth = 14f * (1f - prog * 0.3f),
                    )
                    // Impact spark at the beam head
                    drawCircle(
                        color = Color.White.copy(alpha = 0.85f),
                        radius = 6f + 8f * prog,
                        center = end,
                    )
                }
                drawBeam(addressCenter, addrP)
                drawBeam(dockCenter, dockP)
            }

            // Elements materialize as their beams land. `materialized` drives their alpha/scale
            // until the final settle (when `t` takes over and eases them to 1).
            // Once the laser phase is done (lp == 1) the regular ghost pass (driven by `t`)
            // takes over, so these materialized copies stop drawing to avoid double-rendering.
            val addrMat = if (lp < 1f) addrP else 0f
            val dockMat = if (lp < 1f) dockP else 0f

            if (addrMat > 0.01f && addressCenter != null) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { addressCenter = it.boundsInRoot().center },
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                val s = 0.5f + 0.5f * addrMat
                                scaleX = s
                                scaleY = s
                                alpha = (addrMat * 1.1f).coerceAtMost(1f)
                            },
                    ) {
                        AddressBar(tab = tab, blockTrackers = blockTrackers, lang = lang, onHome = {}, onSubmit = {}, onReloadOrStop = {})
                    }
                }
            }
            if (dockMat > 0.01f && dockCenter != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .onGloballyPositioned { dockCenter = it.boundsInRoot().center },
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                val s = 0.5f + 0.5f * dockMat
                                scaleX = s
                                scaleY = s
                                alpha = (dockMat * 1.1f).coerceAtMost(1f)
                            },
                    ) {
                        BottomDock(
                            lang = lang,
                            canBack = true,
                            canForward = canForward,
                            tabCount = tabCount,
                            onBack = {},
                            onForward = {},
                            onNewTab = {},
                            onTabs = {},
                            onMenu = {},
                        )
                    }
                }
            }
        }

        // Ripple ring that fires once when the orb finishes materializing.
        if (ring.value < 1f) {
            val ringSize = orbPx * (1f + ring.value * 1.1f)
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            (x.value + orbPx / 2f - ringSize / 2f).roundToInt(),
                            (y.value + orbPx / 2f - ringSize / 2f).roundToInt(),
                        )
                    }
                    .size(with(density) { ringSize.toDp() })
                    .border(
                        width = with(density) { (3.dp.toPx() * (1f - ring.value)).coerceAtLeast(0.5f).toDp() },
                        color = Palette.Pink.copy(alpha = (1f - ring.value) * 0.7f),
                        shape = CircleShape,
                    ),
            )
        }

        // ---------- Orb menu: controls fly out of the orb, staggered ----------
        if (menuT.value > 0.01f) {
            // Per-item progress: small stagger, and the reverse plays naturally on close.
            fun itemFly(i: Int): Modifier = Modifier.graphicsLayer {
                val p = (menuT.value * 1.9f - i * 0.15f).coerceIn(0f, 1f)
                val c = itemCenters[i]
                if (c != null) {
                    translationX = (x.value + orbPx / 2f - c.x) * (1f - p)
                    translationY = (y.value + orbPx / 2f - c.y) * (1f - p)
                }
                val s = 0.3f + 0.7f * p
                scaleX = s
                scaleY = s
                alpha = p
            }

            val panelOffsetY = (y.value + orbPx / 2f - panelHPx / 2f)
                .coerceIn(marginPx, (maxH - panelHPx - marginPx).coerceAtLeast(marginPx))
            Row(
                Modifier
                    .offset { IntOffset(0, panelOffsetY.roundToInt()) }
                    .fillMaxWidth()
                    .padding(
                        start = if (dockedRight) margin else margin + orbSize + gap,
                        end = if (dockedRight) margin + orbSize + gap else margin,
                    )
                    .graphicsLayer { alpha = menuT.value }
                    .height(panelHeight)
                    .liquidGlass(999.dp)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // 0: back, 1: forward, 2: address bar, 3: reload/stop, 4: tabs, 5: exit fullscreen.
                Box(Modifier.onGloballyPositioned { itemCenters[0] = it.boundsInRoot().center }) {
                    RoundIconButton(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = tx(lang, "Back", "Kembali"),
                        onClick = { onMenuOpenChange(false); onBack() },
                        modifier = itemFly(0),
                        size = btnSize,
                        iconSize = 22.dp,
                        tint = Palette.Ink,
                    )
                }
                Box(Modifier.onGloballyPositioned { itemCenters[1] = it.boundsInRoot().center }) {
                    RoundIconButton(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = tx(lang, "Forward", "Maju"),
                        onClick = { onMenuOpenChange(false); onForward() },
                        modifier = itemFly(1),
                        size = btnSize,
                        iconSize = 22.dp,
                        tint = if (canForward) Palette.Ink else Palette.Ink.copy(alpha = 0.3f),
                        enabled = canForward,
                    )
                }
                Box(
                    Modifier
                        .weight(1f)
                        .onGloballyPositioned { itemCenters[2] = it.boundsInRoot().center },
                ) {
                    Box(itemFly(2)) {
                        OrbAddress(lang = lang, tab = tab, onSubmit = {
                            onMenuOpenChange(false)
                            onSubmit(it)
                        })
                    }
                }
                Box(Modifier.onGloballyPositioned { itemCenters[3] = it.boundsInRoot().center }) {
                    val loading = tab.progress < 100
                    RoundIconButton(
                        if (loading) Icons.Filled.Close else Icons.Filled.Refresh,
                        contentDescription = if (loading) tx(lang, "Stop", "Berhenti") else tx(lang, "Reload", "Muat ulang"),
                        onClick = onReloadOrStop,
                        modifier = itemFly(3),
                        size = btnSize,
                        iconSize = 18.dp,
                        tint = Palette.Ink,
                    )
                }
                Box(Modifier.onGloballyPositioned { itemCenters[4] = it.boundsInRoot().center }) {
                    Box(
                        itemFly(4).size(btnSize).clip(CircleShape).press { onMenuOpenChange(false); onTabs() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier.size(20.dp).border(2.dp, Palette.Ink, RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("$tabCount", color = Palette.Ink, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Box(Modifier.onGloballyPositioned { itemCenters[5] = it.boundsInRoot().center }) {
                    RoundIconButton(
                        Icons.Filled.FullscreenExit,
                        contentDescription = tx(lang, "Exit fullscreen", "Keluar layar penuh"),
                        onClick = onExit,
                        modifier = itemFly(5),
                        size = btnSize,
                        iconSize = 20.dp,
                        tint = Palette.Pink,
                    )
                }
            }
        }

        // ---------- The orb itself ----------
        // Grows out of the absorbed chrome (1 - t), so the bars visibly BECOME the orb.
        // During EXITING it breathes harder while the lasers fire.
        val breathing = rememberInfiniteTransition(label = "orb-breathe")
        val breathe by breathing.animateFloat(
            initialValue = 1f,
            targetValue = 1.06f,
            animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "breathe",
        )
        val orbScale by animateFloatAsState(
            targetValue = (1f - t.value) * (if (dragging) 1.12f else breathe) * (1f + laserT.value * 0.18f),
            animationSpec = spring(dampingRatio = 0.55f, stiffness = 420f),
            label = "orb-scale",
        )
        Box(
            Modifier
                .offset { IntOffset(x.value.roundToInt(), y.value.roundToInt()) }
                .size(orbSize)
                .graphicsLayer {
                    scaleX = orbScale
                    scaleY = orbScale
                }
                .accent(CircleShape)
                .border(1.5.dp, Color.White.copy(alpha = 0.55f), CircleShape)
                .pointerInput(fs) {
                    detectTapGestures(
                        onTap = {
                            when (fs) {
                                FsState.ORB -> onMenuOpenChange(true)
                                FsState.MENU_OPEN -> onMenuOpenChange(false)
                                else -> Unit // mid-transition taps are ignored
                            }
                        },
                        onLongPress = {
                            if (fs == FsState.ORB || fs == FsState.MENU_OPEN) onExit()
                        },
                    )
                }
                .pointerInput(fs) {
                    detectDragGestures(
                        onDragStart = {
                            if (fs == FsState.MENU_OPEN) onMenuOpenChange(false)
                            if (fs == FsState.ORB) dragging = true
                        },
                        onDragEnd = {
                            if (dragging) {
                                dragging = false
                                dock()
                            }
                        },
                        onDragCancel = {
                            if (dragging) {
                                dragging = false
                                dock()
                            }
                        },
                    ) { change, amount ->
                        if (!dragging) return@detectDragGestures
                        change.consume()
                        scope.launch {
                            x.snapTo((x.value + amount.x).coerceIn(0f, (maxW - orbPx).coerceAtLeast(0f)))
                            y.snapTo((y.value + amount.y).coerceIn(0f, (maxH - orbPx).coerceAtLeast(0f)))
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.AutoAwesome,
                contentDescription = tx(lang, "Fullscreen controls. Long-press to exit.", "Kontrol layar penuh. Tahan untuk keluar."),
                tint = Palette.OnAccent,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/** Compact address pill used inside the orb's control panel. Focus only on user tap. */
@Composable
private fun OrbAddress(lang: Lang, tab: Tab, onSubmit: (String) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var field by remember { mutableStateOf(TextFieldValue("")) }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(editing) {
        if (editing) runCatching { focus.requestFocus() }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(CircleShape)
            .press(enabled = !editing) {
                field = TextFieldValue(tab.url, TextRange(0, tab.url.length))
                editing = true
            }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            Icons.Filled.Lock,
            contentDescription = null,
            tint = Palette.InkMuted,
            modifier = Modifier.size(11.dp),
        )
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (editing) {
                BasicTextField(
                    value = field,
                    onValueChange = { field = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Palette.Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                    cursorBrush = SolidColor(Palette.Pink),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        onSubmit(field.text)
                        editing = false
                        focusManager.clearFocus()
                    }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            } else {
                Text(
                    tab.host,
                    color = Palette.Ink,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
