package id.psw.vshlauncher

object Consts {
    const val CATEGORY_SORT_INDEX_PREFIX: String = "sortIndexCategory"
    const val XMB_DEFAULT_ITEM_ID: String = "id_null"
    const val XMB_DEFAULT_ITEM_DISPLAY_NAME : String = "No Name"
    const val XMB_DEFAULT_ITEM_DESCRIPTION : String = "No Description"
    const val XMB_DEFAULT_ITEM_VALUE : String = "No Value"
    const val XMB_KEY_APP_SORT_MODE = "sort_mode"
    const val XMB_ACTIVE_SEARCH_QUERY : String = "active_search"
    const val XMB_DEFAULT_RESOLUTION = 0x050002D0
    const val ACTION_WAVE_SETTINGS_WIZARD = "id.psw.vshlauncher.ACTION_WAVE_SETTINGS_WIZARD"
    const val ACTION_UI_TEST_DIALOG = "id.psw.vshlauncher.ACTION_UI_TEST_DIALOG"
    const val INTENT_ACT_PLUGIN_STATUS_BAR = "id.psw.vshlauncher.plugin.action.STATUS_BAR"
    const val INTENT_CAT_PLUGIN_STATUS_BAR = "id.psw.vshlauncher.plugin.category.STATUS_BAR"
    const val INTENT_ACT_PLUGIN_ICON_PROVIDER = "id.psw.vshlauncher.plugin.action.ICON_PROVIDER"
    const val INTENT_CAT_PLUGIN_ICON_PROVIDER = "id.psw.vshlauncher.plugin.category.ICON_PROVIDER"
    const val INTENT_ACT_PLUGIN_VISUALIZER = "id.psw.vshlauncher.plugin.action.VISUALIZER_PROVIDER"
    const val INTENT_CAT_PLUGIN_VISUALIZER = "id.psw.vshlauncher.plugin.category.VISUALIZER_PROVIDER"
}

@Suppress("SpellCheckingInspection")
object PrefEntry{
    /** Boolean, Display Wave Wallpaper as internal layer, for use with  */
    const val USES_INTERNAL_WAVE_LAYER: String = "/crosslauncher/wave/beat_angel_escalayer"
    /** Int, shell layout mode */
    const val MENU_LAYOUT = "/crosslauncher/xmbview/layout"
    /** Int, controller glyph style */
    const val BUTTON_DISPLAY_TYPE = "/crosslauncher/button"
    /** Boolean */
    const val DISABLE_EPILEPSY_WARNING = "/crosslauncher/coldboot/skipEpilepsyWarning"
    /** String, Format : "lang|country|variant", Empty = System */
    const val SYSTEM_LANGUAGE = "/setting/system/language"
    /** Int, 0 = O Confirm, 1 = X Confirm*/
    const val CONFIRM_BUTTON = "/setting/system/buttonAssign"
    /** Int, Format : ((Short)w << 16 | (Short)h) */
    const val REFERENCE_RESOLUTION = "/setting/display/0/resolution"
    /** Int, See [android.content.pm.ActivityInfo.screenOrientation] */
    const val DISPLAY_ORIENTATION = "/setting/display/0/orientation"
    /** Boolean, Show Detailed Memory */
    const val SHOW_DETAILED_MEMORY = "/crosslauncher/debug/showJvmMemory"
    /** Int */
    const val VIDEO_ICON_PLAY_MODE = "/crosslauncher/menu/videoIconPlayMode"
    /** Int */
    const val SYSTEM_VISIBLE_APP_DESC = "/crosslauncher/menu/visibleAppDesc"
    /** Boolean */
    const val SKIP_GAMEBOOT = "/crosslauncher/system/skipGameboot"
    /** Boolean */
    const val SHOW_LAUNCHER_FPS: String = "/crosslauncher/display/showFps"
    /** Boolean */
    const val DISPLAY_BACKDROP = "/crosslauncher/menu/showBackdrop"
    /** Boolean */
    const val BACKGROUND_DIM_OPACITY = "/crosslauncher/xmbview/brightness"
    /** Boolean */
    const val PLAY_BACK_SOUND = "/crosslauncher/menu/playBgSound"
    /** Boolean */
    const val DISPLAY_DISABLE_STATUS_BAR = "/crosslauncher/menu/noStatusBar"
    /** String, Data:
     * - ```{network}``` : Connected Network Name
     * - ```{battery}``` : Current Battery Percent
     * - ```{datetime}``` : Date Time
     * - Other value : Keep
     * */
    const val DISPLAY_STATUS_BAR_FORMAT = "/crosslauncher/menu/statusBarFormat"
    /** String, See [SimpleDateFormat](https://developer.android.com/reference/java/text/SimpleDateFormat) */
    const val DISPLAY_DIGITAL_CLOCK_FORMAT = "/crosslauncher/menu/clockFormat"
    /** Boolean */
    const val DISPLAY_SHOW_CLOCK_SECOND = "/crosslauncher/menu/ps3/analogSecond"
    /** Boolean */
    const val DISPLAY_PSP_PAD_STATUS = "/crosslauncher/menu/psp/padStatus"
    /** Int,
     * - 0 = None
     * - 1 = Analog Clock
     * - 2 = Battery
     * - 3 = Network Strength
     */
    const val DISPLAY_STATUS_DISPLAY = "/crosslauncher/menu/statusBarDisplay"
    /** Int, SurfaceView FPS Limit. Defaults to 60 FPS when unset. */
    const val SURFACEVIEW_FPS_LIMIT = "/crosslauncher/xmb/fps"
    /** Int,
     * - 0 = All
     * - 1 = Status
     * - 2 = Navigation
     * - 3 = None
     */
    const val SYSTEM_STATUS_BAR = "/crosslauncher/system/barDisplay"
    /** Int, Should add background to legacy (non-adaptive) icon?
     * - 0 = Disabled
     * - 1 = Other
     * - 2 = Material You if supported
     * */
    const val ICON_RENDERER_LEGACY_BACKGROUND : String = "/crosslauncher/iconrenderer/legacyBg"
    /** Int, Color, of Legacy Icon Background*/
    const val ICON_RENDERER_LEGACY_BACK_COLOR : String = "/crosslauncher/iconrenderer/legacyBgColor"

    /** Int, Accent and Brightness of Material You, Format : `(accent * 100) + brightness` */
    const val ICON_RENDERER_LEGACY_BACK_MATERIAL_YOU : String = "/crosslauncher/iconrenderer/legacyBgYou"
    /** Boolean, generate wide 16:9 cards for square Android app icons */
    const val ICON_RENDERER_WIDE_CARD : String = "/crosslauncher/iconrenderer/wideCard"
    /** 2Bit Array, Which icon type should be rendered first */
    const val ICON_RENDERER_PRIORITY : String = "/crosslauncher/iconrenderer/loadPriority"
    /** Int, Master Volume */
    const val VOLUME_AUDIO_MASTER :String = "/crosslauncher/audio/volume/master"
    /** Int, Sound Effect Volume (Multiplier of of other volume) */
    const val VOLUME_AUDIO_SFX :String = "/crosslauncher/audio/volume/effect"
    /** Int, Music Volume (used by user items) */
    const val VOLUME_AUDIO_BGM :String = "/crosslauncher/audio/volume/music"
    /** Int, System Music Volume (used by e.g Coldboot & Gameboot) */
    const val VOLUME_AUDIO_SYSBGM :String = "/crosslauncher/audio/volume/sysmusic"
    /** Int, Menu Background Music Volume (used by e.g Coldboot & Gameboot) */
    const val VOLUME_AUDIO_MENUBGM :String = "/crosslauncher/audio/volume/menumusic"
    /** Boolean */
    const val LAUNCHER_TV_INTENT_FIRST : String = "/crosslauncher/launcher/prioritizeTv"
    /** Int, 0 = Use default browser, 1 = Ask every time */
    const val INTERNET_BROWSER_LAUNCH_MODE : String = "/crosslauncher/network/browserLaunchMode"
    /** Int, 0 = Internal Storage, 1 = Memory Stick */
    const val STORAGE_ROOT_LABEL_STYLE : String = "/crosslauncher/shell/storageRootLabelStyle"
    /** String, newline-delimited recent launched game app IDs */
    const val RECENT_GAME_IDS : String = "/crosslauncher/game/recentIds"
    /** String, URL opened by the Online Instruction Manuals node */
    const val NETWORK_ONLINE_MANUALS_URL : String = "/crosslauncher/network/onlineManualsUrl"

    /** Int, What kind of interaction should the side menu interact to
     * - 0 = Tap
     * - 1 = Gesture
     */
    const val SIDEMENU_TOUCH_INTERACTION_MODE : String = "/crosslauncher/sideMenu/touchInteractionMode"

    /** String, Current Theme ID (Path to XTF/ZIP) */
    const val CURRENT_THEME_ID = "/crosslauncher/theme/currentId"

    /** String, SAF URI for User Icon Library */
    const val USER_ICON_LIBRARY_URI = "/crosslauncher/theme/iconLibraryUri"
    /** String, Absolute path to the active custom launcher font */
    const val USER_FONT_PATH = "/crosslauncher/theme/fontPath"
    /** String, Absolute path to the active launcher-owned wallpaper image */
    const val LAUNCHER_WALLPAPER_PATH = "/crosslauncher/theme/launcherWallpaperPath"
    /** String, Marker for the last device boot coldboot was shown on */
    const val LAST_SEEN_BOOT_MARKER = "/crosslauncher/coldboot/lastSeenBootMarker"
    /** Boolean, show Debug Settings in normal settings navigation */
    const val SHOW_DEBUG_SETTINGS = "/crosslauncher/debug/showDebugSettings"

    /** String, Custom Nodes */
    const val CUSTOM_NODES = "/crosslauncher/custom/nodes"
    /** String, Custom URL/deep-link launch items */
    const val CUSTOM_LAUNCH_ITEMS = "/crosslauncher/custom/launchItems"
    /** String, Per-node launch target overrides for configurable built-in nodes */
    const val BUILTIN_NODE_LAUNCH_OVERRIDES = "/crosslauncher/custom/builtinNodeLaunchOverrides"
    /** Set<String>, Built-in default nodes hidden by the user */
    const val HIDDEN_BUILTIN_NODES = "/crosslauncher/custom/builtinNodesHidden"
    /** Set<String>, Built-in default nodes deleted by the user */
    const val DELETED_BUILTIN_NODES = "/crosslauncher/custom/builtinNodesDeleted"
}

enum class FittingMode {
    STRETCH,
    FIT,
    FILL
}

enum class AppItemSorting{
    Name,
    PackageName,
    UpdateTime,
    FileSize
}

object SysBar {
    const val NONE       = 0b0000
    const val STATUS     = 0b0001
    const val NAVIGATION = 0b0010
    const val ALL        = 0b0011
}
