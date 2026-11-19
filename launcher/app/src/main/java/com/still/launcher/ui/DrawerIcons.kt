package com.still.launcher.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Icons for the drawer's system actions, path data from Material Icons (Apache 2.0).
 * Kept here so the app doesn't need the whole material-icons-extended library.
 */
object DrawerIcons {
    val Apps = icon(
        "apps",
        "M4,8h4L8,4L4,4v4zM10,20h4v-4h-4v4zM4,20h4v-4L4,16v4zM4,14h4v-4L4,10v4zM10,14h4v-4h-4v4z" +
            "M16,4v4h4L20,4h-4zM10,8h4L14,4h-4v4zM16,14h4v-4h-4v4zM16,20h4v-4h-4v4z",
    )

    /** Ambient Mode shows photos, so a picture frame. */
    val Screensaver = icon(
        "screensaver",
        "M19,5v14H5V5h14m0,-2H5c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2V5" +
            "c0,-1.1 -0.9,-2 -2,-2zM14.14,11.86l-3,3.87L9,13.14 6,17h12l-3.86,-5.14z",
    )

    val Sleep = icon(
        "sleep",
        "M9.37,5.51C9.19,6.15 9.1,6.82 9.1,7.5c0,4.08 3.32,7.4 7.4,7.4c0.68,0 1.35,-0.09 1.99,-0.27" +
            "C17.45,17.19 14.93,19 12,19c-3.86,0 -7,-3.14 -7,-7C5,9.07 6.81,6.55 9.37,5.51z" +
            "M12,3c-4.97,0 -9,4.03 -9,9s4.03,9 9,9s9,-4.03 9,-9c0,-0.46 -0.04,-0.92 -0.1,-1.36" +
            "c-0.98,1.37 -2.58,2.26 -4.4,2.26c-2.98,0 -5.4,-2.42 -5.4,-5.4c0,-1.81 0.89,-3.42 2.26,-4.4" +
            "C12.92,3.04 12.46,3,12,3L12,3z",
    )

    val Wifi = icon(
        "wifi",
        "M1,9l2,2c4.97,-4.97 13.03,-4.97 18,0l2,-2C16.93,2.93 7.08,2.93 1,9z" +
            "M9,17l3,3 3,-3c-1.65,-1.66 -4.34,-1.66 -6,0z" +
            "M5,13l2,2c2.76,-2.76 7.24,-2.76 10,0l2,-2C15.14,9.14 8.87,9.14 5,13z",
    )

    private fun icon(name: String, path: String) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .addPath(addPathNodes(path), fill = SolidColor(Color.Black))
            .build()
}
