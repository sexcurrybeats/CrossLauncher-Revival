package id.psw.vshlauncher.types

import android.graphics.Color

data class XmbTheme(
    var id: String = "",
    var name: String = "Default",
    var description: String = "",
    var author: String = "Unknown",

    // Wave Settings
    var waveStyle: Byte = 0,
    var waveSpeed: Float = 1.0f,
    var waveDayNight: Boolean = true,
    var waveMonth: Int = 0, // -1: Custom, 0: Current Month, 1-12: Specific Month
    var backgroundColorTop: Int = Color.BLACK,
    var backgroundColorBottom: Int = Color.BLACK,
    var foregroundColorEdge: Int = Color.WHITE,
    var foregroundColorCenter: Int = Color.TRANSPARENT,

    // Asset Overrides (Paths within the theme package)
    var backdropPath: String? = null,
    var backdropOverlayPath: String? = null,
    var backdropPortraitPath: String? = null,
    var backdropPortraitOverlayPath: String? = null,
    var icon0Path: String? = null,
    var snd0Path: String? = null,

    // Map of ID to path within the theme package
    var categoryIcons: Map<String, String>? = null,
    var nodeIcons: Map<String, String>? = null
)
