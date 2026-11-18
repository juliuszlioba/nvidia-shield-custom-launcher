package com.still.launcher

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.still.launcher.data.LauncherApp
import com.still.launcher.data.LauncherApps
import com.still.launcher.data.RecentRecord
import com.still.launcher.data.RecentRepository
import com.still.launcher.data.Wallpaper
import com.still.launcher.data.WallpaperOption
import com.still.launcher.data.WallpaperSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LauncherState(
    val apps: List<LauncherApp> = emptyList(),
    val recent: List<RecentRecord> = emptyList(),
    val wallpapers: List<WallpaperOption> = emptyList(),
    /** Path of the chosen wallpaper, as stored. */
    val wallpaperPath: String? = null,
    val wallpaper: Wallpaper = Wallpaper.None,
)

class LauncherViewModel(app: Application) : AndroidViewModel(app) {

    private val repository by lazy { RecentRepository() }
    private val prefs = app.getSharedPreferences("still", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(LauncherState())
    val state: StateFlow<LauncherState> = _state.asStateFlow()

    /** Called every time the home screen comes back, so new installs, entries and wallpapers show up. */
    fun refresh() {
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) { LauncherApps.load(getApplication()) }
            _state.update { it.copy(apps = apps) }
        }
        refreshWallpaper()
        viewModelScope.launch {
            try {
                val recent = repository.latest(10)
                _state.update { it.copy(recent = recent) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // offline: keep whatever was loaded last time
                Log.w(TAG, "Loading recent records failed", e)
            }
        }
    }

    fun refreshWallpaper() {
        viewModelScope.launch {
            // show what's already indexed straight away, then index new files and look again
            loadWallpapers()
            WallpaperSource.indexPictures(getApplication())
            loadWallpapers()
            makeThumbnails()
        }
    }

    private suspend fun loadWallpapers() {
        val options = withContext(Dispatchers.IO) { runCatching { WallpaperSource.list() }.getOrDefault(emptyList()) }
        // keep thumbnails already made for unchanged files
        val known = _state.value.wallpapers.associateBy { Triple(it.path, it.size, it.modified) }
        val merged = options.map { o -> known[Triple(o.path, o.size, o.modified)] ?: o }
        applyWallpapers(merged, prefs.getString(KEY_WALLPAPER, null))
    }

    /** Makes the drawer's missing thumbnails, one at a time (cached, so only once per file). */
    private suspend fun makeThumbnails() {
        _state.value.wallpapers.filter { it.thumbnail == null }.forEach { option ->
            val thumb = withContext(Dispatchers.IO) { WallpaperSource.thumbnail(getApplication(), option) } ?: return@forEach
            _state.update { s ->
                s.copy(wallpapers = s.wallpapers.map { if (it.path == option.path) it.copy(thumbnail = thumb) else it })
            }
        }
    }

    fun selectWallpaper(option: WallpaperOption) {
        prefs.edit { putString(KEY_WALLPAPER, option.path) }
        applyWallpapers(_state.value.wallpapers, option.path)
    }

    private fun applyWallpapers(options: List<WallpaperOption>, chosenPath: String?) {
        val saved = options.firstOrNull { it.path == chosenPath }
        // the saved choice may be missing only because Android hasn't re-indexed it yet:
        // keep showing it rather than jumping to the first wallpaper
        if (saved == null && chosenPath != null && chosenPath == _state.value.wallpaperPath) {
            _state.update { it.copy(wallpapers = options) }
            return
        }
        val chosen = saved ?: options.firstOrNull()
        _state.update {
            it.copy(
                wallpapers = options,
                wallpaperPath = chosen?.path,
                wallpaper = chosen?.toWallpaper() ?: Wallpaper.None,
            )
        }
    }

    private companion object {
        const val TAG = "LauncherViewModel"
        const val KEY_WALLPAPER = "wallpaper"
    }
}
