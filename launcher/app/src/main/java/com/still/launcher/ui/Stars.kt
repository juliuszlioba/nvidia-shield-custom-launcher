package com.still.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser

private const val STAR =
    "M12 2.2l2.95 6.6 7.2.7-5.45 4.85 1.6 7.05L12 17.7l-6.3 3.7 1.6-7.05L1.85 9.5l7.2-.7z"

/** Five gold stars with halves: a 1–10 rating, as on the website (7 → ★★★½). */
@Composable
fun Stars(rating: Int, modifier: Modifier = Modifier) {
    val path = remember { PathParser().parsePathString(STAR).toPath() }
    val value = rating.coerceIn(0, 10) / 2f
    val size = px(36)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(px(6))) {
        repeat(5) { i ->
            val fill = (value - i).coerceIn(0f, 1f)
            Canvas(Modifier.size(size)) {
                scale(this.size.width / 24f, pivot = Offset.Zero) {
                    if (fill > 0f) {
                        clipRect(0f, 0f, 24f * fill, 24f, ClipOp.Intersect) {
                            drawPath(path, Palette.Gold)
                        }
                    }
                    drawPath(
                        path,
                        if (fill > 0f) Palette.Gold else Palette.Ink.copy(alpha = 0.28f),
                        style = Stroke(width = 1.2f, join = StrokeJoin.Round),
                    )
                }
            }
        }
    }
}
