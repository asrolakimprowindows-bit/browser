package com.multex.browser

/*
 * Fullscreen mode ("immersive"): the address bar and bottom dock collapse into a single
 * floating orb. The orb can be dragged anywhere and snaps to the nearest screen edge when
 * released. Tapping it unfolds a compact control panel on the opposite side of the orb
 * (panel extends left when the orb is docked right, and vice versa). Long-press exits.
 */

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun ImmersiveOrb(
    lang: Lang,
    tab: Tab,
    canForward: Boolean,
    tabCount: Int,
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

        val orbSize = 54.dp
        val margin = 12.dp
        val gap = 8.dp
        val panelHeight = 56.dp

        val maxW = with(density) { maxWidth.toPx() }
        val maxH = with(density) { maxHeight.toPx() }
        val orbPx = with(density) { orbSize.toPx() }
        val marginPx = with(density) { margin.toPx() }
        val panelHPx = with(density) { panelHeight.toPx() }

        // The orb starts docked on the right edge, slightly above center.
        val x = remember { Animatable(maxW - orbPx - marginPx) }
        val y = remember { Animatable(maxH * 0.42f) }
        var dragging by remember { mutableStateOf(false) }
        var expanded by remember { mutableStateOf(false) }

        // Materialize: bouncy pop-in plus a single expanding ripple ring, so the bars
        // visibly "become" the orb.
        val pop = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            pop.animateTo(1f, spring(dampingRatio = 0.52f, stiffness = 320f))
        }
        val ring = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            ring.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
        }

        // The IME shrinks this layer; pull the orb back inside the new bounds.
        LaunchedEffect(maxW, maxH) {
            launch { x.animateTo(x.value.coerceIn(0f, (maxW - orbPx).coerceAtLeast(0f))) }
            launch { y.animateTo(y.value.coerceIn(0f, (maxH - orbPx).coerceAtLeast(0f))) }
        }

        val dockedRight = x.value + orbPx / 2f > maxW / 2f

        fun dock() {
            scope.launch {
                val targetX = if (dockedRight) maxW - orbPx - marginPx else marginPx
                val targetY = y.value.coerceIn(marginPx, (maxH - orbPx - marginPx).coerceAtLeast(marginPx))
                launch { x.animateTo(targetX, spring(dampingRatio = 0.68f, stiffness = 420f)) }
                launch { y.animateTo(targetY, spring(dampingRatio = 0.68f, stiffness = 420f)) }
            }
        }

        val orbScale by animateFloatAsState(
            targetValue = (if (dragging) 1.14f else 1f) * pop.value,
            animationSpec = spring(dampingRatio = 0.5f, stiffness = 500f),
            label = "orb-scale",
        )

        // Ripple ring that fires once when the orb materializes.
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

        // Control panel: unfolds away from the docked edge, vertically centered on the orb.
        val panelOffsetY = (y.value + orbPx / 2f - panelHPx / 2f)
            .coerceIn(marginPx, (maxH - panelHPx - marginPx).coerceAtLeast(marginPx))
        Box(
            Modifier
                .offset { IntOffset(0, panelOffsetY.roundToInt()) }
                .fillMaxWidth()
                .padding(
                    start = if (dockedRight) margin else margin + orbSize + gap,
                    end = if (dockedRight) margin + orbSize + gap else margin,
                ),
        ) {
            AnimatedVisibility(
                visible = expanded && !dragging,
                enter = fadeIn(tween(160)) + expandHorizontally(
                    animationSpec = spring(dampingRatio = 0.72f, stiffness = 480f),
                    expandFrom = if (dockedRight) Alignment.End else Alignment.Start,
                ),
                exit = fadeOut(tween(130)) + shrinkHorizontally(
                    animationSpec = tween(180),
                    shrinkTowards = if (dockedRight) Alignment.End else Alignment.Start,
                ),
            ) {
                // 4 buttons + the address bar in the middle: back, forward, [address], reload, tabs.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(panelHeight)
                        .liquidGlass(999.dp)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    RoundIconButton(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = tx(lang, "Back", "Kembali"),
                        onClick = { expanded = false; onBack() },
                        size = 38.dp,
                        iconSize = 20.dp,
                        tint = Palette.Ink,
                    )
                    RoundIconButton(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = tx(lang, "Forward", "Maju"),
                        onClick = { expanded = false; onForward() },
                        size = 38.dp,
                        iconSize = 20.dp,
                        tint = if (canForward) Palette.Ink else Palette.Ink.copy(alpha = 0.3f),
                        enabled = canForward,
                    )
                    Box(Modifier.weight(1f)) {
                        OrbAddress(lang = lang, tab = tab, onSubmit = {
                            expanded = false
                            onSubmit(it)
                        })
                    }
                    val loading = tab.progress < 100
                    RoundIconButton(
                        if (loading) Icons.Filled.Close else Icons.Filled.Refresh,
                        contentDescription = if (loading) tx(lang, "Stop", "Berhenti") else tx(lang, "Reload", "Muat ulang"),
                        onClick = onReloadOrStop,
                        size = 38.dp,
                        iconSize = 18.dp,
                        tint = Palette.Ink,
                    )
                    Box(
                        Modifier.size(38.dp).clip(CircleShape).press { expanded = false; onTabs() },
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
            }
        }

        // The orb itself: tap toggles the panel, drag moves it (snaps on release),
        // long-press exits fullscreen.
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
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { expanded = !expanded },
                        onLongPress = {
                            expanded = false
                            onExit()
                        },
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            dragging = true
                            expanded = false
                        },
                        onDragEnd = {
                            dragging = false
                            dock()
                        },
                        onDragCancel = {
                            dragging = false
                            dock()
                        },
                    ) { change, amount ->
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

/** Compact address pill used inside the orb's control panel. */
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
            .background(Palette.Ink.copy(alpha = 0.08f))
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
