package com.still.launcher.ui

import android.net.Uri
import android.view.TextureView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil3.compose.AsyncImage
import com.still.launcher.data.Wallpaper
import java.io.File

/**
 * The live wallpaper. [active] is false while another app is in front: the player is then
 * released, so the 4K decoder is free for Plex or YouTube.
 */
@Composable
fun WallpaperView(wallpaper: Wallpaper, active: Boolean, modifier: Modifier = Modifier) {
    when (wallpaper) {
        is Wallpaper.Video -> if (active) key(wallpaper) { VideoWallpaper(File(wallpaper.path), modifier) }
        is Wallpaper.Image -> AsyncImage(
            model = File(wallpaper.path),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
        Wallpaper.None -> Unit
    }
}

@Composable
private fun VideoWallpaper(file: File, modifier: Modifier) {
    val context = LocalContext.current
    var firstFrame by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(if (firstFrame) 1f else 0f, tween(900), label = "videoFade")

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            // no sound and no audio decoding: it must never interrupt anything
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                .build()
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ONE
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            addListener(object : Player.Listener {
                override fun onRenderedFirstFrame() { firstFrame = true }
            })
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(player) { player.play() }

    AndroidView(
        // TextureView (not SurfaceView) so the parallax shift and fade apply to the video too
        factory = { ctx -> TextureView(ctx).also { player.setVideoTextureView(it) } },
        modifier = modifier.fillMaxSize().alpha(alpha),
    )
}
