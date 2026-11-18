package com.still.launcher.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

/** An app in the row, with the official TV banner it ships (or its icon as a fallback). */
data class LauncherApp(
    val packageName: String,
    val label: String,
    val banner: ImageBitmap?,
    val icon: ImageBitmap?,
) {
    val isMoviesNight: Boolean get() = packageName == MOVIES_NIGHT
}

const val MOVIES_NIGHT = "com.moviesnight.tv"

/** The row, in order. Apps that aren't installed are left out. */
val ROW_PACKAGES = listOf(
    "com.google.android.youtube.tv",
    "com.plexapp.android",
    "com.crunchyroll.crunchyroid",
    MOVIES_NIGHT,
    "tv.twitch.android.app",
)

object LauncherApps {

    fun load(context: Context): List<LauncherApp> {
        val pm = context.packageManager
        return ROW_PACKAGES.mapNotNull { pkg ->
            val info = try {
                pm.getApplicationInfo(pkg, 0)
            } catch (e: PackageManager.NameNotFoundException) {
                return@mapNotNull null
            }
            if (!info.enabled) return@mapNotNull null
            val component = launchIntent(context, pkg)?.component
            // A TV app's banner can sit on its launcher activity or on the application
            val banner: Drawable? = component?.let { runCatching { pm.getActivityBanner(it) }.getOrNull() }
                ?: runCatching { pm.getApplicationBanner(pkg) }.getOrNull()
            LauncherApp(
                packageName = pkg,
                label = info.loadLabel(pm).toString(),
                banner = banner?.toBitmap(BANNER_W, BANNER_H)?.asImageBitmap(),
                icon = runCatching { pm.getApplicationIcon(pkg).toBitmap(ICON, ICON).asImageBitmap() }.getOrNull(),
            )
        }
    }

    fun launchIntent(context: Context, pkg: String): Intent? {
        val pm = context.packageManager
        return pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
    }

    fun launch(context: Context, pkg: String) {
        val intent = launchIntent(context, pkg) ?: return
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Opens moviesNight straight on a record's detail screen. */
    fun openMoviesNightRecord(context: Context, recordId: Long) {
        val intent = Intent(Intent.ACTION_MAIN)
            .setClassName(MOVIES_NIGHT, "$MOVIES_NIGHT.MainActivity")
            .putExtra(EXTRA_RECORD_ID, recordId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { launch(context, MOVIES_NIGHT) }
    }

    /** Read by moviesNight's MainActivity. */
    const val EXTRA_RECORD_ID = "com.moviesnight.tv.extra.RECORD_ID"

    // banners are 320×180 dp; decode at 2× for the 1080p xhdpi screen
    private const val BANNER_W = 640
    private const val BANNER_H = 360
    private const val ICON = 192
}
