package com.still.launcher.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.em
import coil3.compose.AsyncImage
import com.still.launcher.data.WallpaperOption
import java.io.File
import kotlin.math.roundToInt

/** A line in the settings drawer: a wallpaper to choose, or an action. */
sealed interface DrawerItem {
    data class Wall(val option: WallpaperOption) : DrawerItem
    data class Action(val label: String, val icon: ImageVector, val note: String? = null, val run: () -> Unit) : DrawerItem
}

// Layout of the drawer in mockup pixels; fixed heights so the list can scroll to the focused line.
private const val PANEL_W = 620f
private const val SIDE = 48f
private const val TOP = 72f
private const val TITLE_BLOCK = 100f
private const val KICKER_BLOCK = 44f
private const val WALL_H = 112f
private const val WALL_STRIDE = 120f
private const val SECTION_GAP = 36f
private const val ACTION_H = 68f
private const val ACTION_STRIDE = 76f
private const val BOTTOM = 72f
private const val SCREEN_H = 1080f
/** Space kept between the focused line and the bottom edge. */
private const val EDGE = 140f

/**
 * The settings drawer, sliding in from the right. [progress] is 0 (closed) to 1 (open).
 * System actions come first, then the wallpapers.
 */
@Composable
fun SettingsDrawer(
    progress: Float,
    items: List<DrawerItem>,
    focused: Int,
    currentWallpaper: String?,
    unitPx: Float,
) {
    if (progress <= 0.001f) return
    // System first (short, always in view), then the wallpapers
    val actions = items.count { it is DrawerItem.Action }
    val walls = items.size - actions
    val actionsTop = TOP + TITLE_BLOCK + KICKER_BLOCK
    val wallsTop = actionsTop + actions * ACTION_STRIDE + SECTION_GAP + KICKER_BLOCK
    val total = wallsTop + walls * WALL_STRIDE + BOTTOM
    val maxScroll = (total - SCREEN_H).coerceAtLeast(0f)

    // scroll only as far as needed to keep the focused line clear of the bottom edge
    val (y, h) = if (focused < actions) actionsTop + focused * ACTION_STRIDE to ACTION_H
    else wallsTop + (focused - actions) * WALL_STRIDE to WALL_H
    val target = (y + h + EDGE - SCREEN_H).coerceIn(0f, maxScroll)
    val scroll by animateFloatAsState(target, tween(450, easing = Ease), label = "drawerScroll")
    val moreBelow by animateFloatAsState(if (scroll < maxScroll - 1f) 1f else 0f, tween(300), label = "moreBelow")

    Box(Modifier.fillMaxSize()) {
        // dim the home screen behind
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f * progress)))

        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .width(px(PANEL_W))
                .fillMaxHeight()
                .graphicsLayer { translationX = (1f - progress) * PANEL_W * unitPx }
                .background(PanelColor)
                .drawBehind {
                    drawLine(Palette.Ink.copy(alpha = 0.08f), Offset(0f, 0f), Offset(0f, size.height), strokeWidth = unitPx)
                }
                .clipToBounds(),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    // the list is taller than the screen: let it take its full height and scroll it
                    .wrapContentHeight(align = Alignment.Top, unbounded = true)
                    .offset { IntOffset(0, -(scroll * unitPx).roundToInt()) }
                    .padding(horizontal = px(SIDE)),
            ) {
                Spacer(Modifier.height(px(TOP)))
                Box(Modifier.height(px(TITLE_BLOCK))) {
                    BasicText("Settings", style = drawerText(44, FontWeight.Light, letterSpacing = -0.01f))
                }
                Kicker("SYSTEM")
                items.forEachIndexed { i, item ->
                    if (item is DrawerItem.Action) {
                        ActionRow(item, i == focused)
                        Spacer(Modifier.height(px(ACTION_STRIDE - ACTION_H)))
                    }
                }
                Spacer(Modifier.height(px(SECTION_GAP)))
                Kicker("WALLPAPER")
                items.forEachIndexed { i, item ->
                    if (item is DrawerItem.Wall) {
                        WallRow(item.option, i == focused, item.option.path == currentWallpaper)
                        Spacer(Modifier.height(px(WALL_STRIDE - WALL_H)))
                    }
                }
            }
            // soft fade at the bottom while there is more to scroll to
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(px(160))
                    .graphicsLayer { alpha = moreBelow }
                    .background(Brush.verticalGradient(0f to PanelColor.copy(alpha = 0f), 1f to PanelColor)),
            )
        }
    }
}

private val PanelColor = Color(0xF20A0C16)

@Composable
private fun drawerText(size: Number, weight: FontWeight, color: Color = Palette.Ink, letterSpacing: Float = 0f) =
    TextStyle(fontFamily = Manrope, fontWeight = weight, fontSize = tpx(size), color = color, letterSpacing = letterSpacing.em)

@Composable
private fun Kicker(label: String) {
    Box(Modifier.height(px(KICKER_BLOCK))) {
        BasicText(label, style = drawerText(14, FontWeight.Medium, Palette.InkDim, letterSpacing = 0.28f))
    }
}

@Composable
private fun rowBackground(focused: Boolean): Color {
    val c by animateColorAsState(
        if (focused) Palette.Ink.copy(alpha = 0.1f) else Palette.Ink.copy(alpha = 0f),
        tween(300, easing = Ease),
        label = "rowBg",
    )
    return c
}

@Composable
private fun WallRow(option: WallpaperOption, focused: Boolean, current: Boolean) {
    val dim by animateFloatAsState(if (focused) 0f else 0.35f, tween(300, easing = Ease), label = "thumbDim")
    Row(
        Modifier
            .fillMaxWidth()
            .height(px(WALL_H))
            .clip(RoundedCornerShape(px(16)))
            .background(rowBackground(focused))
            .padding(horizontal = px(11)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(px(20)),
    ) {
        Box(
            Modifier
                .size(px(160), px(90))
                .clip(RoundedCornerShape(px(10)))
                .background(Color(0xFF1A1C24))
                .drawWithContent {
                    drawContent()
                    if (dim > 0f) drawRect(Color.Black.copy(alpha = dim))
                },
        ) {
            option.thumbnail?.let {
                AsyncImage(File(it), option.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(px(8))) {
            BasicText(
                option.name,
                style = drawerText(22, FontWeight.Normal, Palette.Ink.copy(alpha = if (focused) 1f else 0.78f)),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (current) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(px(8))) {
                    Box(Modifier.size(px(7)).clip(CircleShape).background(Palette.Gold))
                    BasicText("CURRENT", style = drawerText(12, FontWeight.Medium, Palette.Gold, letterSpacing = 0.24f))
                }
            }
        }
    }
}

@Composable
private fun ActionRow(item: DrawerItem.Action, focused: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(px(ACTION_H))
            .clip(RoundedCornerShape(px(16)))
            .background(rowBackground(focused))
            .padding(horizontal = px(22)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(px(18)),
    ) {
        Image(
            item.icon,
            contentDescription = null,
            colorFilter = ColorFilter.tint(Palette.Ink.copy(alpha = if (focused) 0.9f else 0.5f)),
            modifier = Modifier.size(px(26)),
        )
        BasicText(
            item.label,
            style = drawerText(24, FontWeight.Normal, Palette.Ink.copy(alpha = if (focused) 1f else 0.78f)),
            modifier = Modifier.weight(1f),
        )
        item.note?.let { BasicText(it, style = drawerText(16, FontWeight.Light, Palette.InkDim)) }
    }
}
