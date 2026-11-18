package com.still.launcher.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.still.launcher.R

/*
 * Everything is laid out in the mockup's 1920×1080 pixels and scaled to the real screen,
 * so the numbers here match mockups/mockup-a-still.html one to one.
 */
val LocalUnit = staticCompositionLocalOf { 0.5.dp }

/** Mockup pixels → dp. */
@Composable
@ReadOnlyComposable
fun px(value: Number): Dp = LocalUnit.current * value.toFloat()

/** Mockup pixels → text size. */
@Composable
@ReadOnlyComposable
fun tpx(value: Number): TextUnit = with(LocalDensity.current) { px(value).toSp() }

object Palette {
    val Ink = Color(0xFFEEF0F6)
    val InkDim = Ink.copy(alpha = 0.55f)
    val Gold = Color(0xFFFFC81E)
    val Background = Color(0xFF040A1E)
    val Shade = Color(0xFF040610)
    val Dim = Color(0xFF06070C)
}

val Ease = CubicBezierEasing(0.22f, 0.8f, 0.24f, 1f)

@OptIn(ExperimentalTextApi::class)
val Manrope = FontFamily(
    listOf(200, 300, 400, 500, 600).map { w ->
        Font(R.font.manrope, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    },
)

/** CSS-style `saturate(s) brightness(b)` as a colour matrix. */
fun dimMatrix(saturation: Float, brightness: Float): ColorMatrix =
    ColorMatrix().apply {
        setToSaturation(saturation)
        timesAssign(
            ColorMatrix(
                floatArrayOf(
                    brightness, 0f, 0f, 0f, 0f,
                    0f, brightness, 0f, 0f, 0f,
                    0f, 0f, brightness, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
    }
