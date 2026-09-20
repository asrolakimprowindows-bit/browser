package com.multex.browser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* ---------- Clickable helpers ---------- */

/** Click without the Material ripple, matching the flat web buttons. */
fun Modifier.press(enabled: Boolean = true, role: Role = Role.Button, onClick: () -> Unit): Modifier =
    clickable(
        interactionSource = MutableInteractionSource(),
        indication = null,
        enabled = enabled,
        role = role,
        onClick = onClick,
    )

/* ---------- LetterTile ---------- */

enum class TileSize(val dp: Dp, val radius: Dp, val text: TextUnit) {
    SM(24.dp, 6.dp, 10.sp),
    MD(36.dp, 12.dp, 14.sp),
    LG(56.dp, 16.dp, 20.sp),
}

@Composable
fun LetterTile(label: String, tint: Color, size: TileSize = TileSize.MD, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(size.radius)
    Box(
        modifier
            .size(size.dp)
            .shadow(if (size == TileSize.SM) 2.dp else 6.dp, shape)
            .clip(shape)
            .background(Brush.linearGradient(listOf(tint, lerp(tint, Color.White, 0.35f)))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label.take(1),
            color = Palette.TileInk,
            fontFamily = Fonts.Display,
            fontWeight = FontWeight.Bold,
            fontSize = size.text,
        )
    }
}

/* ---------- Round icon button (grid size-N place-items-center rounded-full) ---------- */

@Composable
fun RoundIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    iconSize: Dp = 16.dp,
    tint: Color = Palette.InkMuted,
    background: Color = Color.Transparent,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .press(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/* ---------- Switch ---------- */

@Composable
fun GlassSwitch(checked: Boolean, onChange: (Boolean) -> Unit, label: String) {
    val thumbOffset by animateDpAsState(if (checked) 24.dp else 4.dp, tween(180), label = "thumb")
    Box(
        Modifier
            .size(width = 48.dp, height = 28.dp)
            .clip(CircleShape)
            .background(if (checked) Palette.Pink else Palette.Ink.copy(alpha = 0.15f))
            .semantics { contentDescription = label }
            .press(role = Role.Switch) { onChange(!checked) },
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = thumbOffset)
                .size(20.dp)
                .shadow(2.dp, CircleShape)
                .background(Color.White, CircleShape),
        )
    }
}

/* ---------- Segmented control ---------- */

@Composable
fun <T> Segmented(
    value: T,
    options: List<Pair<T, String>>,
    onChange: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .inkFill(RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { (id, label) ->
            val selected = id == value
            Box(
                Modifier
                    .weight(1f)
                    .then(
                        if (selected) Modifier.shadow(3.dp, RoundedCornerShape(8.dp)).background(Palette.Pink, RoundedCornerShape(8.dp))
                        else Modifier,
                    )
                    .clip(RoundedCornerShape(8.dp))
                    .press(role = Role.RadioButton) { onChange(id) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (selected) Palette.OnAccent else Palette.InkMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/* ---------- Settings rows ---------- */

@Composable
fun SectionTitle(text: String, first: Boolean = false) {
    Text(
        text.uppercase(),
        color = Palette.InkMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp,
        modifier = Modifier.padding(top = if (first) 0.dp else 16.dp, bottom = 4.dp),
    )
}

@Composable
fun SettingRow(
    title: String,
    description: String? = null,
    stacked: Boolean = false,
    trailing: @Composable () -> Unit,
) {
    if (stacked) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            RowText(title, description)
            trailing()
        }
    } else {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.weight(1f)) { RowText(title, description) }
            trailing()
        }
    }
}

@Composable
private fun RowText(title: String, description: String?) {
    Column {
        Text(title, color = Palette.Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        if (description != null) Text(description, color = Palette.InkMuted, fontSize = 12.sp)
    }
}

/* ---------- Pill buttons ---------- */

@Composable
fun RowScope.PillButton(
    label: String,
    icon: ImageVector? = null,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    PillButtonBase(label, icon, accent, onClick, Modifier.weight(1f))
}

@Composable
fun PillButtonFull(label: String, icon: ImageVector? = null, accent: Boolean = false, onClick: () -> Unit) {
    PillButtonBase(label, icon, accent, onClick, Modifier.fillMaxWidth())
}

@Composable
private fun PillButtonBase(label: String, icon: ImageVector?, accent: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .then(if (accent) Modifier.accent(shape) else Modifier.glass(shape))
            .press(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val color = if (accent) Palette.OnAccent else Palette.Ink
        if (icon != null) Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(label, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

/* ---------- Bottom sheet overlay ---------- */

@Composable
fun BoxScope.OverlaySheet(
    title: String,
    subtitle: String? = null,
    onClose: () -> Unit,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .press(role = Role.Image, onClick = onClose),
    )

    BoxWithConstraints(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
        val maxSheet = maxHeight * 0.86f
        AnimatedVisibility(
            visibleState = visible,
            enter = fadeIn(tween(220)) + slideInVertically(tween(300)) { it / 4 },
        ) {
            val shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxSheet)
                    .shadow(24.dp, shape)
                    .glassStrong(shape)
                    .press(role = Role.Image) { }
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .imePadding(),
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 12.dp)
                        .size(width = 48.dp, height = 6.dp)
                        .background(Palette.Ink.copy(alpha = 0.2f), CircleShape),
                )
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(title, color = Palette.Ink, fontFamily = Fonts.Display, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        if (subtitle != null) Text(subtitle, color = Palette.InkMuted, fontSize = 12.sp)
                    }
                    RoundIconButton(
                        Icons.Rounded.Close,
                        contentDescription = "Close",
                        onClick = onClose,
                        size = 36.dp,
                        iconSize = 16.dp,
                        tint = Palette.Ink,
                        background = Palette.Ink.copy(alpha = 0.1f),
                    )
                }
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth(),
                    content = content,
                )
                if (footer != null) {
                    HorizontalDivider(color = Palette.GlassBorder)
                    Box(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) { footer() }
                }
            }
        }
    }
}

@Composable
fun VSpace(height: Dp) = Spacer(Modifier.height(height))

@Composable
fun HSpace(width: Dp) = Spacer(Modifier.width(width))
