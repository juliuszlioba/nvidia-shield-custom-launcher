package com.still.launcher.data

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.os.Environment
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.coroutines.resume

/** What sits behind the app row. File size and date are part of it, so a replaced file counts as new. */
sealed interface Wallpaper {
    data class Video(val path: String, val size: Long, val modified: Long) : Wallpaper
    data class Image(val path: String, val size: Long, val modified: Long) : Wallpaper
    data object None : Wallpaper
}

/** One choice in the settings drawer: the video (or image) inside a `Pictures/live-wallpaper-N` folder. */
data class WallpaperOption(
    val path: String,
    val name: String,
    val size: Long,
    val modified: Long,
    /** Small still frame for the drawer; null until it has been made. */
    val thumbnail: String? = null,
) {
    val isVideo: Boolean get() = File(path).extension.lowercase() in WallpaperSource.VIDEO

    fun toWallpaper(): Wallpaper =
        if (isVideo) Wallpaper.Video(path, size, modified) else Wallpaper.Image(path, size, modified)
}

/**
 * Wallpapers live on the Shield's storage in `Pictures/live-wallpaper-1`, `-2`, … (copied there over
 * "Transfer files over local network"). Each folder holds one; a new folder shows up in the drawer
 * the next time the home screen appears.
 */
object WallpaperSource {
    val VIDEO = setOf("mp4", "mkv", "webm", "mov", "m4v")
    private val IMAGE = setOf("jpg", "jpeg", "png", "webp")
    private val FOLDER = Regex("""live-wallpaper-(\d+)""")

    private val pictures: File get() = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)

    fun list(): List<WallpaperOption> =
        pictures.listFiles()
            ?.mapNotNull { dir -> FOLDER.matchEntire(dir.name)?.let { dir to it.groupValues[1].toInt() } }
            ?.sortedBy { it.second }
            ?.mapNotNull { (dir, _) -> pick(dir) }
            .orEmpty()

    /**
     * Files copied over the network share skip Android's media index, and on Android 11 an app
     * only sees indexed media in shared storage. Scanning Pictures indexes new or replaced
     * wallpapers (the scanner walks the folder; unchanged files are skipped quickly).
     */
    suspend fun indexPictures(context: Context) {
        withTimeoutOrNull(20_000) {
            suspendCancellableCoroutine { cont ->
                MediaScannerConnection.scanFile(context, arrayOf(pictures.absolutePath), null) { _, _ ->
                    if (cont.isActive) cont.resume(Unit)
                }
            }
        }
    }

    /** The folder's video if it has one, otherwise its image. */
    private fun pick(dir: File): WallpaperOption? {
        val files = dir.listFiles()?.filter { it.isFile && !it.name.startsWith(".") }?.sortedBy { it.name.lowercase() }
            ?: return null
        val file = files.firstOrNull { it.extension.lowercase() in VIDEO }
            ?: files.firstOrNull { it.extension.lowercase() in IMAGE }
            ?: return null
        return WallpaperOption(file.absolutePath, displayName(file), file.length(), file.lastModified())
    }

    /** "black-cat-looking-at-you-moewalls-com.mp4" → "Black Cat Looking at You". */
    fun displayName(file: File): String {
        val small = setOf("a", "an", "and", "at", "in", "of", "on", "the", "to")
        return file.nameWithoutExtension
            .replace(Regex("""[-_ ]?moewalls[-_.]?com$""", RegexOption.IGNORE_CASE), "")
            .split('-', '_', ' ')
            .filter { it.isNotBlank() }
            .mapIndexed { i, w -> if (i > 0 && w.lowercase() in small) w.lowercase() else w.replaceFirstChar(Char::uppercase) }
            .joinToString(" ")
            .ifBlank { file.nameWithoutExtension }
    }

    /** A cached 320×180 frame from a few seconds in (or the image itself). */
    fun thumbnail(context: Context, option: WallpaperOption): String? {
        if (!option.isVideo) return option.path
        val dir = File(context.cacheDir, "thumbs").apply { mkdirs() }
        val out = File(dir, "${(option.path + option.size + option.modified).hashCode().toUInt()}.jpg")
        if (out.exists()) return out.absolutePath
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(option.path)
            val frame = retriever.getScaledFrameAtTime(3_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 320, 180)
                ?: return null
            out.outputStream().use { frame.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            frame.recycle()
            out.absolutePath
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }
}
