package com.still.launcher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import com.still.launcher.data.LauncherApps
import com.still.launcher.ui.LauncherActions
import com.still.launcher.ui.LauncherScreen

class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()

    /** Bumped when Home is pressed while the launcher is already showing. */
    private var homeSignal by mutableIntStateOf(0)

    private val storagePermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            viewModel.refreshWallpaper()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val actions = LauncherActions(
            launchApp = { LauncherApps.launch(this, it.packageName) },
            openRecord = { LauncherApps.openMoviesNightRecord(this, it) },
            // The stock launcher's "Your apps" grid, else the Apps list in Settings
            openAllApps = {
                open(
                    Intent().setClassName("com.google.android.tvlauncher", "com.google.android.tvlauncher.appsview.AppsViewActivity"),
                    Intent(Intent.ACTION_ALL_APPS),
                    Intent(Settings.ACTION_APPLICATION_SETTINGS),
                )
            },
            selectWallpaper = { viewModel.selectWallpaper(it) },
            openNetwork = { open(Intent(Settings.ACTION_WIFI_SETTINGS), Intent(Settings.ACTION_WIRELESS_SETTINGS)) },
            // SystemUI's "dream now" activity starts the chosen screensaver (Ambient Mode)
            startScreensaver = { open(Intent().setClassName("com.android.systemui", "com.android.systemui.Somnambulator")) },
            // On the SHIELD this TV Settings screen puts the device to sleep straight away
            sleep = { open(Intent().setClassName("com.android.tv.settings", "com.android.tv.settings.system.PowerSettingsActivity")) },
            openSystemSettings = { open(Intent(Settings.ACTION_SETTINGS)) },
        )

        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
            LauncherScreen(
                state = state,
                active = lifecycle.isAtLeast(Lifecycle.State.STARTED),
                homeSignal = homeSignal,
                actions = actions,
            )
        }

        requestStorageIfNeeded()
    }

    override fun onStart() {
        super.onStart()
        viewModel.refresh()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        homeSignal++
    }

    /** Starts the first intent that works. */
    private fun open(vararg intents: Intent) {
        for (intent in intents) {
            if (runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
        }
    }

    private fun requestStorageIfNeeded() {
        val needed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) storagePermission.launch(missing.toTypedArray())
    }
}
