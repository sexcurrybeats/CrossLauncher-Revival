package id.psw.vshlauncher.submodules

import id.psw.vshlauncher.BuildConfig
import id.psw.vshlauncher.PrefEntry
import id.psw.vshlauncher.Vsh
import id.psw.vshlauncher.addToCategory
import id.psw.vshlauncher.submodules.settings.AndroidSystemSettings
import id.psw.vshlauncher.submodules.settings.AudioSettings
import id.psw.vshlauncher.submodules.settings.DebugSettings
import id.psw.vshlauncher.submodules.settings.DisplaySettings
import id.psw.vshlauncher.submodules.settings.MediaSettings
import id.psw.vshlauncher.submodules.settings.RootDirSettings
import id.psw.vshlauncher.submodules.settings.SystemSettings
import id.psw.vshlauncher.submodules.settings.ThemeSettings
import kotlinx.coroutines.launch

class SettingsSubmodule(private val ctx : Vsh) : IVshSubmodule
{
    companion object {
        const val CATEGORY_SETTINGS_WAVE = "settings_category_wave"
        const val CATEGORY_SETTINGS_THEME = "settings_category_theme"
        const val CATEGORY_SETTINGS_ANDROID = "settings_category_android"
        const val CATEGORY_SETTINGS_DISPLAY = "settings_category_display"
        const val CATEGORY_SETTINGS_MEDIA = "settings_category_media"
        const val CATEGORY_SETTINGS_AUDIO = "settings_category_audio"
        const val CATEGORY_SETTINGS_DEBUG = "settings_category_debug"
        const val CATEGORY_SETTINGS_SYSTEMINFO = "settings_category_systeminfo"
        const val CATEGORY_SETTINGS_SYSTEM = "settings_category_system"
    }

    val display         = DisplaySettings(ctx)
    val theme           = ThemeSettings(ctx)
    val audio           = AudioSettings(ctx)
    val system          = SystemSettings(ctx)
    val media           = MediaSettings(ctx)
    val android         = AndroidSystemSettings(ctx)
    val debug           = DebugSettings(ctx)
    val rootDir = RootDirSettings(ctx)

    fun isDebugSettingsVisible(): Boolean {
        return ctx.M.pref.get(PrefEntry.SHOW_DEBUG_SETTINGS, BuildConfig.DEBUG)
    }

    fun setDebugSettingsVisible(visible: Boolean) {
        ctx.M.pref.set(PrefEntry.SHOW_DEBUG_SETTINGS, visible).push()
        syncDebugSettingsCategory()
    }

    fun syncDebugSettingsCategory() {
        val settingsCategory = ctx.categories.find { it.id == Vsh.ITEM_CATEGORY_SETTINGS } ?: return
        settingsCategory.content.removeAll { it.id == CATEGORY_SETTINGS_DEBUG }

        if (!isDebugSettingsVisible()) return

        val insertIndex = settingsCategory.content.indexOfFirst {
            it.id == "settings_system_update" || it.id == "settings_install_package"
        }.takeIf { it >= 0 } ?: settingsCategory.content.size
        settingsCategory.content.add(insertIndex, debug.createCategory())
    }

    fun fillSettings(){
        ctx.lifeScope.launch {
            ctx.addToCategory(Vsh.ITEM_CATEGORY_SETTINGS, theme.createCategory())
            ctx.addToCategory(Vsh.ITEM_CATEGORY_SETTINGS, display.createCategory())
            ctx.addToCategory(Vsh.ITEM_CATEGORY_SETTINGS, audio.createCategory())
            ctx.addToCategory(Vsh.ITEM_CATEGORY_SETTINGS, media.createCategory())
            ctx.addToCategory(Vsh.ITEM_CATEGORY_SETTINGS, system.createCategory())
            ctx.addToCategory(Vsh.ITEM_CATEGORY_SETTINGS, android.createCategory())
            ctx.addToCategory(Vsh.ITEM_CATEGORY_SETTINGS, rootDir.settingsAddSystemUpdate())
            syncDebugSettingsCategory()

        }
    }

    override fun onCreate() {
    }

    override fun onDestroy() {

    }
}
