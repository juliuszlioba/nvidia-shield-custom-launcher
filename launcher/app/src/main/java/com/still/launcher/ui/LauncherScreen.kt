package com.still.launcher.ui

import android.view.KeyEvent.KEYCODE_BUTTON_A
import android.view.KeyEvent.KEYCODE_DPAD_CENTER
import android.view.KeyEvent.KEYCODE_DPAD_DOWN
import android.view.KeyEvent.KEYCODE_DPAD_LEFT
import android.view.KeyEvent.KEYCODE_DPAD_RIGHT
import android.view.KeyEvent.KEYCODE_DPAD_UP
import android.view.KeyEvent.KEYCODE_ENTER
import android.view.KeyEvent.KEYCODE_NUMPAD_ENTER
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.em
import coil3.compose.AsyncImage
import com.still.launcher.LauncherState
import com.still.launcher.data.LauncherApp
import com.still.launcher.data.RecentRecord
import com.still.launcher.data.WallpaperOption
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

class LauncherActions(
    val launchApp: (LauncherApp) -> Unit,
    val openRecord: (Long) -> Unit,
    val selectWallpaper: (WallpaperOption) -> Unit,
    val openNetwork: () -> Unit,
    val startScreensaver: () -> Unit,
    val sleep: () -> Unit,
    val restart: () -> Unit,
    val openSystemSettings: () -> Unit,
)

/** Where the remote's focus is: the app row, the "Recently seen" posters, or the settings gear. */
private enum class Zone { Apps, Recent, Gear }

/** A card in "Recently seen": one of the records, or the closing "More on moviesNight". */
private sealed interface Card {
    data class Record(val record: RecentRecord) : Card
    data object More : Card
}

@Composable
fun LauncherScreen(state: LauncherState, active: Boolean, homeSignal: Int, actions: LauncherActions) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Palette.Background)) {
        CompositionLocalProvider(LocalUnit provides maxWidth / 1920f) {
            LauncherContent(state, active, homeSignal, actions)
        }
    }
}

@Composable
private fun LauncherContent(state: LauncherState, active: Boolean, homeSignal: Int, actions: LauncherActions) {
    val apps = state.apps
    val unitPx = with(LocalDensity.current) { LocalUnit.current.toPx() }

    var cur by rememberSaveable { mutableIntStateOf(0) }
    var zone by remember { mutableStateOf(Zone.Apps) }
    var gearReturn by remember { mutableStateOf(Zone.Apps) }
    var rc by remember { mutableIntStateOf(0) }
    var drawerOpen by remember { mutableStateOf(false) }
    var di by remember { mutableIntStateOf(0) }

    val sel = cur.coerceIn(0, (apps.size - 1).coerceAtLeast(0))
    val current = apps.getOrNull(sel)
    val moviesNight = apps.firstOrNull { it.isMoviesNight }
    val cards: List<Card> = remember(state.recent) {
        if (state.recent.isEmpty()) emptyList() else state.recent.map { Card.Record(it) } + Card.More
    }
    val showRecent = current?.isMoviesNight == true && cards.isNotEmpty()
    val rcSafe = rc.coerceIn(0, (cards.size - 1).coerceAtLeast(0))

    // Leaving moviesNight hides the strip and starts it from the newest entry next time
    LaunchedEffect(showRecent) {
        if (!showRecent) {
            rc = 0
            if (zone == Zone.Recent) zone = Zone.Apps
            if (gearReturn == Zone.Recent) gearReturn = Zone.Apps
        }
    }
    // Home pressed while already home: back to the app row
    LaunchedEffect(homeSignal) { zone = Zone.Apps; rc = 0; drawerOpen = false }

    // ---------- Settings drawer: system actions, then wallpapers ----------
    val drawerItems: List<DrawerItem> = remember(state.wallpapers, actions) {
        listOf<DrawerItem>(
            DrawerItem.Action("Wi-Fi & network", run = actions.openNetwork),
            DrawerItem.Action("Screensaver", run = actions.startScreensaver),
            DrawerItem.Action("Sleep", run = actions.sleep),
            DrawerItem.Action("Restart", note = "About → Reboot", run = actions.restart),
            DrawerItem.Action("System settings", run = actions.openSystemSettings),
        ) + state.wallpapers.map { DrawerItem.Wall(it) }
    }
    val diSafe = di.coerceIn(0, (drawerItems.size - 1).coerceAtLeast(0))
    fun openDrawer() {
        di = 0
        drawerOpen = true
    }
    fun activateDrawer() {
        when (val item = drawerItems.getOrNull(diSafe)) {
            is DrawerItem.Wall -> actions.selectWallpaper(item.option)
            is DrawerItem.Action -> item.run()
            null -> Unit
        }
    }

    val focus = remember { FocusRequester() }
    LaunchedEffect(active) { if (active) runCatching { focus.requestFocus() } }
    BackHandler {
        if (drawerOpen) drawerOpen = false else if (zone != Zone.Apps) zone = Zone.Apps
    }

    fun activate() {
        when (zone) {
            Zone.Apps -> current?.let(actions.launchApp)
            Zone.Gear -> openDrawer()
            Zone.Recent -> when (val card = cards.getOrNull(rcSafe)) {
                is Card.Record -> actions.openRecord(card.record.id)
                Card.More -> moviesNight?.let(actions.launchApp)
                null -> Unit
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(focus)
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val code = e.nativeKeyEvent.keyCode
                val repeat = e.nativeKeyEvent.repeatCount > 0
                if (drawerOpen) {
                    when (code) {
                        KEYCODE_DPAD_UP -> di = (diSafe - 1).coerceAtLeast(0)
                        KEYCODE_DPAD_DOWN -> di = (diSafe + 1).coerceAtMost(drawerItems.lastIndex)
                        KEYCODE_DPAD_LEFT -> drawerOpen = false
                        KEYCODE_DPAD_RIGHT -> Unit
                        KEYCODE_DPAD_CENTER, KEYCODE_ENTER, KEYCODE_NUMPAD_ENTER, KEYCODE_BUTTON_A ->
                            if (!repeat) activateDrawer()
                        else -> return@onPreviewKeyEvent false
                    }
                    return@onPreviewKeyEvent true
                }
                when (code) {
                    KEYCODE_DPAD_LEFT -> when (zone) {
                        Zone.Apps -> cur = (sel - 1).coerceAtLeast(0)
                        Zone.Recent -> rc = (rcSafe - 1).coerceAtLeast(0)
                        Zone.Gear -> Unit
                    }
                    KEYCODE_DPAD_RIGHT -> when (zone) {
                        Zone.Apps -> cur = (sel + 1).coerceAtMost(apps.lastIndex.coerceAtLeast(0))
                        Zone.Recent -> rc = (rcSafe + 1).coerceAtMost(cards.lastIndex)
                        Zone.Gear -> Unit
                    }
                    KEYCODE_DPAD_UP -> when (zone) {
                        Zone.Apps -> if (showRecent) zone = Zone.Recent else { gearReturn = Zone.Apps; zone = Zone.Gear }
                        Zone.Recent -> { gearReturn = Zone.Recent; zone = Zone.Gear }
                        Zone.Gear -> Unit
                    }
                    KEYCODE_DPAD_DOWN -> when (zone) {
                        Zone.Apps -> Unit
                        Zone.Recent -> zone = Zone.Apps
                        Zone.Gear -> zone = if (gearReturn == Zone.Recent && showRecent) Zone.Recent else Zone.Apps
                    }
                    KEYCODE_DPAD_CENTER, KEYCODE_ENTER, KEYCODE_NUMPAD_ENTER, KEYCODE_BUTTON_A ->
                        if (!repeat) activate()
                    else -> return@onPreviewKeyEvent false
                }
                true
            }
            .focusable(),
    ) {
        // ---------- Wallpaper: oversized so it can drift a little with focus ----------
        val mid = (apps.size - 1) / 2f
        val parallax by animateFloatAsState((mid - sel) * 14f, tween(1600, easing = Ease), label = "parallax")
        WallpaperView(
            wallpaper = state.wallpaper,
            active = active,
            modifier = Modifier
                .align(Alignment.Center)
                .requiredSize(px(2080), px(1170))
                .graphicsLayer { translationX = parallax * unitPx },
        )
        // brightness(.7) and the soft shades from the mockup
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Palette.Shade.copy(alpha = 0.45f), 0.22f to Palette.Shade.copy(alpha = 0f)),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0.4f to Palette.Shade.copy(alpha = 0f),
                    0.7f to Palette.Shade.copy(alpha = 0.6f),
                    1f to Palette.Shade.copy(alpha = 0.94f),
                ),
            ),
        )
        val dim by animateFloatAsState(if (showRecent) 1f else 0f, tween(700, easing = Ease), label = "dim")
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = dim }
                .background(
                    Brush.verticalGradient(
                        0f to Palette.Dim.copy(alpha = 0.9f),
                        0.58f to Palette.Dim.copy(alpha = 0.84f),
                        1f to Palette.Dim.copy(alpha = 0.55f),
                    ),
                ),
        )

        // ---------- Settings gear + clock, top right ----------
        Row(
            Modifier.align(Alignment.TopEnd).padding(top = px(60), end = px(100)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(px(34)),
        ) {
            Gear(focused = zone == Zone.Gear, unitPx = unitPx)
            Clock(active)
        }

        // ---------- Recently seen (moviesNight) ----------
        val panelAlpha by animateFloatAsState(if (showRecent) 1f else 0f, tween(550, easing = Ease), label = "panelAlpha")
        val panelShift by animateFloatAsState(if (showRecent) 0f else 18f, tween(700, easing = Ease), label = "panelShift")
        if (panelAlpha > 0.005f && cards.isNotEmpty()) {
            Box(
                Modifier
                    .offset(y = px(150))
                    .fillMaxWidth()
                    .height(px(470))
                    .graphicsLayer { alpha = panelAlpha; translationY = panelShift * unitPx },
            ) {
                RecentInfo(cards[rcSafe], Modifier.offset(x = px(140), y = px(25)).size(px(540), px(420)))
                PosterStrip(
                    cards = cards,
                    selected = rcSafe,
                    focused = zone == Zone.Recent,
                    moviesNightBanner = moviesNight?.banner,
                    unitPx = unitPx,
                    modifier = Modifier.offset(x = px(740)).width(px(1180)).fillMaxHeight(),
                )
            }
        }

        // ---------- App row ----------
        Row(
            Modifier.align(Alignment.TopCenter).offset(y = px(700)),
            horizontalArrangement = Arrangement.spacedBy(px(44)),
            verticalAlignment = Alignment.Bottom,
        ) {
            apps.forEachIndexed { i, app ->
                key(app.packageName) {
                    AppTile(app, selected = i == sel, lifted = i == sel && zone == Zone.Apps, unitPx = unitPx)
                }
            }
        }

        // ---------- Name + position pips ----------
        Column(
            Modifier.align(Alignment.TopCenter).offset(y = px(925)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(px(20)),
        ) {
            AnimatedContent(
                targetState = current?.label.orEmpty(),
                transitionSpec = { fadeIn(tween(250, delayMillis = 120)) togetherWith fadeOut(tween(150)) },
                label = "name",
            ) { name ->
                BasicText(name, style = text(26, FontWeight.Normal, letterSpacing = 0.02f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(px(8))) {
                apps.indices.forEach { i ->
                    val w by animateFloatAsState(if (i == sel) 24f else 6f, tween(500, easing = Ease), label = "pip")
                    Box(
                        Modifier
                            .size(px(w), px(6))
                            .clip(RoundedCornerShape(px(3)))
                            .background(Palette.Ink.copy(alpha = if (i == sel) 0.9f else 0.28f)),
                    )
                }
            }
        }

        // ---------- Settings drawer, over everything ----------
        val drawerProgress by animateFloatAsState(if (drawerOpen) 1f else 0f, tween(450, easing = Ease), label = "drawer")
        SettingsDrawer(drawerProgress, drawerItems, diSafe, state.wallpaperPath, unitPx)
    }
}

// ============================================================================================

@Composable
private fun text(
    size: Number,
    weight: FontWeight,
    color: Color = Palette.Ink,
    letterSpacing: Float = 0f,
    lineHeight: Float? = null,
) = TextStyle(
    fontFamily = Manrope,
    fontWeight = weight,
    fontSize = tpx(size),
    color = color,
    letterSpacing = letterSpacing.em,
    lineHeight = lineHeight?.let { tpx(size.toFloat() * it) } ?: androidx.compose.ui.unit.TextUnit.Unspecified,
)

/** Small spaced capitals: "RECENTLY SEEN", "WATCHED 14 NOV. 2026". */
@Composable
private fun kicker() = text(14, FontWeight.Medium, Palette.InkDim, letterSpacing = 0.28f)

@Composable
private fun Clock(active: Boolean) {
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(active) {
        while (true) {
            now = LocalTime.now()
            delay(60_000L - System.currentTimeMillis() % 60_000L + 50)
        }
    }
    val hm = DateTimeFormatter.ofPattern("HH:mm").format(now)
    BasicText(
        buildAnnotatedString {
            append(hm.substring(0, 2))
            withStyle(SpanStyle(color = Palette.Ink.copy(alpha = 0.45f))) { append(":") }
            append(hm.substring(3))
        },
        style = text(30, FontWeight.Light, Palette.Ink.copy(alpha = 0.8f), letterSpacing = 0.03f)
            .copy(fontFeatureSettings = "tnum"),
    )
}

/** Settings: a quiet gear next to the clock that only lights up when focused. */
@Composable
private fun Gear(focused: Boolean, unitPx: Float) {
    val f by animateFloatAsState(if (focused) 1f else 0f, tween(400, easing = Ease), label = "gear")
    Box(
        Modifier
            .size(px(30))
            .drawBehind {
                if (f > 0f) {
                    drawCircle(
                        color = Palette.Ink.copy(alpha = 0.7f * f),
                        radius = size.minDimension / 2 + 10 * unitPx,
                        style = Stroke(2 * unitPx),
                    )
                }
            },
    ) {
        Image(
            Icons.Outlined.Settings,
            contentDescription = "Settings",
            colorFilter = ColorFilter.tint(Palette.Ink),
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.35f + 0.65f * f },
        )
    }
}

@Composable
private fun AppTile(app: LauncherApp, selected: Boolean, lifted: Boolean, unitPx: Float) {
    val t by animateFloatAsState(if (selected) 1f else 0f, tween(500, easing = Ease), label = "sel")
    val scale by animateFloatAsState(if (lifted) 1.1f else if (selected) 1.04f else 1f, tween(500, easing = Ease), label = "scale")
    val lift by animateFloatAsState(if (lifted) -6f else 0f, tween(500, easing = Ease), label = "lift")
    val ring by animateFloatAsState(if (lifted) 0.7f else 0f, tween(500, easing = Ease), label = "ring")
    val shape = RoundedCornerShape(px(18))
    Box(
        Modifier
            .size(px(288), px(162))
            .graphicsLayer {
                scaleX = scale; scaleY = scale
                translationY = lift * unitPx
                transformOrigin = TransformOrigin(0.5f, 1f)
            }
            .drawBehind { focusRing(ring, 18f, unitPx) }
            .clip(shape)
            .background(Color(0xFF16181F)),
    ) {
        val filter = ColorFilter.colorMatrix(dimMatrix(0.5f + 0.5f * t, 0.45f + 0.55f * t))
        if (app.banner != null) {
            Image(app.banner, app.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, colorFilter = filter)
        } else {
            // no TV banner: icon + name
            Row(
                Modifier.fillMaxSize().padding(horizontal = px(20)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(px(14), Alignment.CenterHorizontally),
            ) {
                app.icon?.let { Image(it, null, Modifier.size(px(56)), colorFilter = filter) }
                BasicText(app.label, style = text(28, FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** The thin rounded ring around whatever the remote is on: 6px gap, 2px line. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.focusRing(alpha: Float, radiusPx: Float, unitPx: Float) {
    if (alpha <= 0f) return
    val o = 7 * unitPx
    drawRoundRect(
        color = Palette.Ink.copy(alpha = alpha),
        topLeft = Offset(-o, -o),
        size = Size(size.width + 2 * o, size.height + 2 * o),
        cornerRadius = CornerRadius((radiusPx + 7) * unitPx),
        style = Stroke(2 * unitPx),
    )
}

// ---------- Recently seen ----------

@Composable
private fun RecentInfo(card: Card, modifier: Modifier) {
    AnimatedContent(
        targetState = card,
        transitionSpec = { fadeIn(tween(220, delayMillis = 140)) togetherWith fadeOut(tween(160)) },
        modifier = modifier,
        label = "info",
    ) { c ->
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Column {
                BasicText("RECENTLY SEEN", style = kicker())
                Spacer(Modifier.height(px(22)))
                BasicText(
                    if (c is Card.Record) c.record.title else "More on moviesNight",
                    style = text(62, FontWeight.Light, letterSpacing = -0.015f, lineHeight = 1.06f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(px(12)))
                BasicText(
                    when (c) {
                        is Card.Record -> listOfNotNull(c.record.year?.toString(), c.record.kind).joinToString(" · ")
                        Card.More -> "Everything I recently watched, rated and reviewed"
                    },
                    style = text(22, FontWeight.Light, Palette.InkDim, letterSpacing = 0.02f),
                )
            }
            if (c is Card.Record) {
                Column {
                    c.record.watchedOn?.let {
                        BasicText("WATCHED ${formatWatched(it).uppercase()}", style = kicker())
                    }
                    Stars(c.record.rating, Modifier.padding(top = px(18)))
                    Row(
                        Modifier.padding(top = px(18)).heightIn(min = px(34)),
                        horizontalArrangement = Arrangement.spacedBy(px(12)),
                    ) {
                        c.record.recommendation?.let { rec ->
                            // the curly quote sits high in the em box: nudge it up to the cap line like the mockup
                            BasicText("“", style = text(46, FontWeight.Medium, Palette.Gold, lineHeight = 0.9f), modifier = Modifier.offset(y = px(-6)))
                            BasicText(
                                rec,
                                style = text(25, FontWeight.Medium, lineHeight = 1.35f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

private val MONTHS = listOf("Jan.", "Feb.", "Mar.", "Apr.", "May", "June", "July", "Aug.", "Sep.", "Oct.", "Nov.", "Dec.")
private fun formatWatched(d: java.time.LocalDate) = "${d.dayOfMonth} ${MONTHS[d.monthValue - 1]} ${d.year}"

@Composable
private fun PosterStrip(
    cards: List<Card>,
    selected: Int,
    focused: Boolean,
    moviesNightBanner: ImageBitmap?,
    unitPx: Float,
    modifier: Modifier,
) {
    Box(
        modifier
            // fade the posters out towards the right edge of the screen
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent {
                drawContent()
                drawRect(
                    Brush.horizontalGradient(0f to Color.Black, 0.78f to Color.Black, 0.99f to Color.Transparent),
                    blendMode = BlendMode.DstIn,
                )
            },
    ) {
        cards.forEachIndexed { i, card ->
            key(i) { Poster(card, d = i - selected, ring = focused && i == selected, moviesNightBanner, unitPx) }
        }
    }
}

private const val BIG_W = 280f
private const val BIG_H = 420f
private const val SMALL_W = 210f
private const val SMALL_H = 315f
private const val GAP = 40f

@Composable
private fun Poster(card: Card, d: Int, ring: Boolean, moviesNightBanner: ImageBitmap?, unitPx: Float) {
    val spec = tween<Float>(600, easing = Ease)
    val big = d == 0
    val w by animateFloatAsState(if (big) BIG_W else SMALL_W, spec, label = "w")
    val h by animateFloatAsState(if (big) BIG_H else SMALL_H, spec, label = "h")
    val x by animateFloatAsState(
        when {
            d < 0 -> d * (SMALL_W + GAP)
            d == 0 -> 0f
            else -> BIG_W + GAP + (d - 1) * (SMALL_W + GAP)
        },
        spec, label = "x",
    )
    val alpha by animateFloatAsState(if (d < 0) 0f else 1f, tween(500, easing = Ease), label = "a")
    val bright by animateFloatAsState(if (big) 1f else 0.72f, tween(500, easing = Ease), label = "b")
    val ringA by animateFloatAsState(if (ring) 0.75f else 0f, tween(400, easing = Ease), label = "ring")
    if (x > 1300f) return // far off to the right, not visible

    Box(
        Modifier
            .offset { IntOffset(((20f + x) * unitPx).roundToInt(), ((25f + (BIG_H - h) / 2f) * unitPx).roundToInt()) }
            .size(px(w), px(h))
            .graphicsLayer { this.alpha = alpha }
            .drawBehind { focusRing(ringA, 14f, unitPx) }
            .clip(RoundedCornerShape(px(14)))
            .background(Color(0xFF12141C))
            // dim the whole card evenly (same as CSS brightness()), background and text included
            .drawWithContent {
                drawContent()
                if (bright < 1f) drawRect(Color.Black.copy(alpha = 1f - bright))
            },
    ) {
        when (card) {
            is Card.Record -> AsyncImage(
                model = card.record.posterUrl,
                contentDescription = card.record.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Card.More -> MoreCard(moviesNightBanner)
        }
    }
}

/** Last card: "More on" + the moviesNight banner logo, centred. */
@Composable
private fun MoreCard(banner: ImageBitmap?) {
    // match the card to the banner's own background so its edges disappear
    val bg = remember(banner) {
        banner?.let { runCatching { Color(it.asAndroidBitmap().getPixel(4, 4)) }.getOrNull() } ?: Color(0xFF111111)
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(bg), contentAlignment = Alignment.Center) {
        if (banner != null) {
            Image(
                banner,
                "moviesNight",
                Modifier.requiredWidth(maxWidth * 1.08f).aspectRatio(16f / 9f),
                contentScale = ContentScale.Fit,
            )
        } else {
            BasicText("moviesNight", style = text(30, FontWeight.Normal))
        }
        BasicText(
            "MORE ON",
            style = text(15, FontWeight.Medium, Palette.InkDim, letterSpacing = 0.28f),
            modifier = Modifier.offset(y = -(maxHeight * 0.13f) - px(10)),
        )
    }
}
