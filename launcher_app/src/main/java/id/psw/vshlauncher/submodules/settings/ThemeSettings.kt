package id.psw.vshlauncher.submodules.settings

import id.psw.vshlauncher.*
import id.psw.vshlauncher.activities.Xmb
import id.psw.vshlauncher.submodules.IconManager
import id.psw.vshlauncher.submodules.SettingsSubmodule
import id.psw.vshlauncher.submodules.SfxType
import id.psw.vshlauncher.types.XmbItem
import id.psw.vshlauncher.types.XmbTheme
import id.psw.vshlauncher.types.items.XmbAndroidSettingShortcutItem
import id.psw.vshlauncher.types.items.XmbMenuItem
import id.psw.vshlauncher.types.items.XmbNodeItem
import id.psw.vshlauncher.types.items.XmbSettingsCategory
import id.psw.vshlauncher.types.items.XmbSettingsItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ThemeSettings(private val vsh: Vsh) : ISettingsCategories(vsh) {
    private data class ShellNodeTarget(
        val categoryName: String,
        val node: XmbNodeItem
    )

    private data class SettingsIconTarget(
        val displayName: String,
        val id: String
    )

    private fun getShellIconCategoryTargets() = ArrayList(vsh.categories)

    private fun getShellIconNodeTargets(): List<ShellNodeTarget> {
        return vsh.categories.flatMap { category ->
            category.content
                .filterIsInstance<XmbNodeItem>()
                .map { node -> ShellNodeTarget(category.displayName, node) }
        }
    }

    private fun getShellIconSettingsTargets(): List<SettingsIconTarget> {
        val settingsRoot = vsh.categories.find { it.id == Vsh.ITEM_CATEGORY_SETTINGS } ?: return emptyList()
        val targets = arrayListOf<SettingsIconTarget>()

        fun collectTargets(items: List<XmbItem>, path: List<String>) {
            items.filter { !it.isHidden }.forEach { item ->
                when (item) {
                    is XmbSettingsCategory -> {
                        val nextPath = path + item.displayName
                        targets.add(SettingsIconTarget(nextPath.joinToString(" / "), item.id))
                        collectTargets(item.content, nextPath)
                    }
                    is XmbSettingsItem -> {
                        targets.add(SettingsIconTarget((path + item.displayName).joinToString(" / "), item.id))
                    }
                    is XmbAndroidSettingShortcutItem -> {
                        val nextPath = path + item.displayName
                        targets.add(SettingsIconTarget(nextPath.joinToString(" / "), item.id))
                        if (item.hasContent) {
                            collectTargets(item.content, nextPath)
                        }
                    }
                }
            }
        }

        collectTargets(settingsRoot.content, listOf(settingsRoot.displayName))
        return targets
    }

    private fun applyTheme(theme: XmbTheme) {
        M.pref.set(PrefEntry.CURRENT_THEME_ID, theme.id).push()

        // Reload active theme assets in customizer
        M.customizer.loadActiveTheme()

        // Update Wave Settings if present in theme
        if (theme.id.isNotEmpty()) {
            M.settings.display.wave.setWaveStyle(theme.waveStyle)
            M.settings.display.wave.setWaveMonth(theme.waveMonth)
            // TODO: Apply more theme parameters
        }

        vsh.postNotification(R.drawable.category_setting, "Theme Applied", theme.name)
    }

    private fun mkItemThemeSelection(): XmbSettingsItem {
        val themes = M.customizer.themes

        val text = {
            val currentId = M.pref.get(PrefEntry.CURRENT_THEME_ID, "")
            if (currentId.isEmpty()) "Default"
            else themes.find { it.id == currentId }?.name ?: "Unknown"
        }

        val item = XmbSettingsItem(vsh, "settings_theme_selection",
            R.string.settings_category_theme_name,
            R.string.settings_category_theme_desc,
            R.drawable.category_setting, text,
            {
                vsh.xmbView?.showSideMenu(true)
            }
        ).apply {
            hasMenu = true
            val menuItems = ArrayList<XmbMenuItem>()

            // Add Default Theme
            menuItems.add(XmbMenuItem.XmbMenuItemLambda({ "Default" }, { false }, 0) {
                applyTheme(XmbTheme(id = "", name = "Default"))
            })

            // Add Discovered Themes
            themes.forEachIndexed { index, theme ->
                menuItems.add(XmbMenuItem.XmbMenuItemLambda({ theme.name }, { false }, index + 1) {
                    applyTheme(theme)
                })
            }
            this.menuItems = menuItems
        }
        return item
    }

    private fun mkItemStorageLabelMode(): XmbSettingsItem {
        val labelMap = linkedMapOf(
            0 to R.string.settings_theme_storage_label_internal,
            1 to R.string.settings_theme_storage_label_memory_stick
        )

        val item = XmbSettingsItem(
            vsh,
            "settings_theme_storage_label_mode",
            R.string.settings_theme_storage_label_name,
            R.string.settings_theme_storage_label_desc,
            R.drawable.category_setting,
            {
                val mode = M.pref.get(PrefEntry.STORAGE_ROOT_LABEL_STYLE, 0)
                vsh.getString(labelMap[mode] ?: R.string.unknown)
            }
        ) {
            vsh.xmbView?.showSideMenu(true)
        }

        item.hasMenu = true
        item.menuItems = arrayListOf<XmbMenuItem>().apply {
            labelMap.entries.forEachIndexed { index, (mode, label) ->
                add(XmbMenuItem.XmbMenuItemLambda({ vsh.getString(label) }, { false }, index) {
                    M.pref.set(PrefEntry.STORAGE_ROOT_LABEL_STYLE, mode).push()
                    vsh.xmbView?.showSideMenu(false)
                    vsh.postNotification(
                        R.drawable.category_setting,
                        vsh.getString(R.string.settings_theme_storage_label_name),
                        vsh.getString(R.string.settings_theme_storage_label_applied)
                    )
                    vsh.lifeScope.launch {
                        delay(600)
                        vsh.restart()
                    }
                })
            }
        }

        return item
    }

    private fun mkItemPickCustomFont(): XmbSettingsItem {
        return XmbSettingsItem(
            vsh,
            "settings_theme_custom_font_pick",
            R.string.settings_theme_custom_font_pick_name,
            R.string.settings_theme_custom_font_pick_desc,
            R.drawable.ic_open,
            { vsh.M.font.getActiveFontName() }
        ) {
            (vsh.xmbView?.context as? Xmb)?.customFontPicker?.launch("*/*")
        }
    }

    private fun mkItemResetFont(): XmbSettingsItem {
        return XmbSettingsItem(
            vsh,
            "settings_theme_custom_font_reset",
            R.string.settings_theme_custom_font_reset_name,
            R.string.settings_theme_custom_font_reset_desc,
            R.drawable.category_setting,
            { vsh.getString(R.string.common_default) }
        ) {
            vsh.M.font.resetUserFont()
            vsh.postNotification(R.drawable.category_setting, vsh.getString(R.string.common_success), vsh.getString(R.string.common_default))
            vsh.xmbView?.context?.xmb?.recreate()
        }
    }

    private fun mkItemPickLauncherWallpaper(): XmbSettingsItem {
        return XmbSettingsItem(
            vsh,
            "settings_theme_pick_launcher_wallpaper",
            R.string.settings_theme_pick_launcher_wallpaper_name,
            R.string.settings_theme_pick_launcher_wallpaper_desc,
            R.drawable.ic_open,
            {
                if (vsh.M.pref.get(PrefEntry.LAUNCHER_WALLPAPER_PATH, "").isBlank()) {
                    vsh.getString(R.string.common_default)
                } else {
                    vsh.getString(R.string.common_change)
                }
            }
        ) {
            (vsh.xmbView?.context as? Xmb)?.launcherWallpaperPicker?.launch("image/*")
        }
    }

    private fun mkItemResetLauncherWallpaper(): XmbSettingsItem {
        return XmbSettingsItem(
            vsh,
            "settings_theme_reset_launcher_wallpaper",
            R.string.settings_theme_reset_launcher_wallpaper_name,
            R.string.settings_theme_reset_launcher_wallpaper_desc,
            R.drawable.category_setting,
            { vsh.getString(R.string.common_default) }
        ) {
            vsh.M.customizer.resetLauncherWallpaper()
            vsh.postNotification(
                R.drawable.category_setting,
                vsh.getString(R.string.common_success),
                vsh.getString(R.string.settings_theme_reset_launcher_wallpaper_success)
            )
        }
    }

    private fun mkItemPickColdbootImage(): XmbSettingsItem {
        return XmbSettingsItem(
            vsh,
            "settings_theme_coldboot_image_pick",
            R.string.settings_theme_coldboot_image_pick_name,
            R.string.settings_theme_coldboot_image_pick_desc,
            R.drawable.ic_open,
            { "" }
        ) {
            (vsh.xmbView?.context as? Xmb)?.coldbootImagePicker?.launch("image/*")
        }
    }

    private fun mkItemPickColdbootAudio(): XmbSettingsItem {
        return XmbSettingsItem(
            vsh,
            "settings_theme_coldboot_audio_pick",
            R.string.settings_theme_coldboot_audio_pick_name,
            R.string.settings_theme_coldboot_audio_pick_desc,
            R.drawable.ic_component_audio,
            { "" }
        ) {
            (vsh.xmbView?.context as? Xmb)?.coldbootAudioPicker?.launch("audio/*")
        }
    }

    private fun mkItemResetColdboot(): XmbSettingsItem {
        return XmbSettingsItem(
            vsh,
            "settings_theme_coldboot_reset",
            R.string.settings_theme_coldboot_reset_name,
            R.string.settings_theme_coldboot_reset_desc,
            R.drawable.ic_delete,
            { vsh.getString(R.string.common_default) }
        ) {
            val deleted = vsh.M.customizer.resetColdbootOverrides()
            vsh.postNotification(
                R.drawable.category_setting,
                vsh.getString(R.string.common_success),
                vsh.getString(R.string.settings_theme_coldboot_reset_success, deleted)
            )
        }
    }

    private fun mkItemPickGamebootImage(): XmbSettingsItem {
        return XmbSettingsItem(
            vsh,
            "settings_theme_gameboot_image_pick",
            R.string.settings_theme_gameboot_image_pick_name,
            R.string.settings_theme_gameboot_image_pick_desc,
            R.drawable.ic_open,
            { "" }
        ) {
            (vsh.xmbView?.context as? Xmb)?.gamebootImagePicker?.launch("*/*")
        }
    }

    private fun mkItemPickGamebootAudio(): XmbSettingsItem {
        return XmbSettingsItem(
            vsh,
            "settings_theme_gameboot_audio_pick",
            R.string.settings_theme_gameboot_audio_pick_name,
            R.string.settings_theme_gameboot_audio_pick_desc,
            R.drawable.ic_component_audio,
            { "" }
        ) {
            (vsh.xmbView?.context as? Xmb)?.gamebootAudioPicker?.launch("audio/*")
        }
    }

    private fun mkItemResetGameboot(): XmbSettingsItem {
        return XmbSettingsItem(
            vsh,
            "settings_theme_gameboot_reset",
            R.string.settings_theme_gameboot_reset_name,
            R.string.settings_theme_gameboot_reset_desc,
            R.drawable.ic_delete,
            { vsh.getString(R.string.common_default) }
        ) {
            val deleted = vsh.M.customizer.resetGamebootOverrides()
            vsh.postNotification(
                R.drawable.category_setting,
                vsh.getString(R.string.common_success),
                vsh.getString(R.string.settings_theme_gameboot_reset_success, deleted)
            )
        }
    }

    private fun mkItemPickBatteryGlyph(): XmbSettingsItem {
        return XmbSettingsItem(
            vsh,
            "settings_theme_battery_glyph_pick",
            R.string.settings_theme_battery_glyph_pick_name,
            R.string.settings_theme_battery_glyph_pick_desc,
            R.drawable.icon_battery,
            { "" }
        ) {
            (vsh.xmbView?.context as? Xmb)?.batteryGlyphPicker?.launch("image/*")
        }
    }

    private fun mkItemResetBatteryGlyph(): XmbSettingsItem {
        return XmbSettingsItem(
            vsh,
            "settings_theme_battery_glyph_reset",
            R.string.settings_theme_battery_glyph_reset_name,
            R.string.settings_theme_battery_glyph_reset_desc,
            R.drawable.ic_delete,
            { vsh.getString(R.string.common_default) }
        ) {
            val deleted = vsh.M.customizer.resetBatteryGlyphOverride()
            vsh.postNotification(
                R.drawable.category_setting,
                vsh.getString(R.string.common_success),
                vsh.getString(R.string.settings_theme_battery_glyph_reset_success, deleted)
            )
        }
    }

    private fun mkItemPickMenuSfx(type: SfxType): XmbSettingsItem {
        val name = when(type) {
            SfxType.Selection -> R.string.settings_audio_sfx_pick_selection_name
            SfxType.Confirm -> R.string.settings_audio_sfx_pick_confirm_name
            SfxType.Cancel -> R.string.settings_audio_sfx_pick_cancel_name
        }
        return XmbSettingsItem(
            vsh,
            "settings_theme_resource_pick_sfx_${type.name.lowercase()}",
            name,
            R.string.settings_audio_sfx_pick_desc,
            R.drawable.ic_open,
            { "" }
        ) {
            vsh.M.audio.openSfxPicker(type)
        }
    }

    private fun mkItemResetMenuSfx(type: SfxType): XmbSettingsItem {
        val name = when(type) {
            SfxType.Selection -> R.string.settings_audio_sfx_reset_selection_name
            SfxType.Confirm -> R.string.settings_audio_sfx_reset_confirm_name
            SfxType.Cancel -> R.string.settings_audio_sfx_reset_cancel_name
        }
        return XmbSettingsItem(
            vsh,
            "settings_theme_resource_reset_sfx_${type.name.lowercase()}",
            name,
            R.string.settings_audio_sfx_reset_desc,
            R.drawable.ic_delete,
            { "" }
        ) {
            vsh.M.audio.resetSfxOverride(type)
        }
    }

    private fun mkCategoryFontResources(): XmbSettingsCategory {
        return XmbSettingsCategory(
            vsh,
            "settings_theme_resource_fonts",
            R.drawable.category_setting,
            R.string.settings_theme_resource_fonts_name,
            R.string.settings_theme_resource_fonts_desc
        ).apply {
            content.addAllV(
                mkItemPickCustomFont(),
                mkItemResetFont()
            )
        }
    }

    private fun mkCategoryWallpaperResources(): XmbSettingsCategory {
        return XmbSettingsCategory(
            vsh,
            "settings_theme_resource_wallpaper",
            R.drawable.category_photo,
            R.string.settings_theme_resource_wallpaper_name,
            R.string.settings_theme_resource_wallpaper_desc
        ).apply {
            content.addAllV(
                mkItemPickLauncherWallpaper(),
                mkItemResetLauncherWallpaper()
            )
        }
    }

    private fun mkCategoryColdbootResources(): XmbSettingsCategory {
        return XmbSettingsCategory(
            vsh,
            "settings_theme_resource_coldboot",
            R.drawable.category_setting,
            R.string.settings_theme_resource_coldboot_name,
            R.string.settings_theme_resource_coldboot_desc
        ).apply {
            content.addAllV(
                mkItemPickColdbootImage(),
                mkItemPickColdbootAudio(),
                mkItemResetColdboot()
            )
        }
    }

    private fun mkCategoryGamebootResources(): XmbSettingsCategory {
        return XmbSettingsCategory(
            vsh,
            "settings_theme_resource_gameboot",
            R.drawable.category_games,
            R.string.settings_theme_resource_gameboot_name,
            R.string.settings_theme_resource_gameboot_desc
        ).apply {
            content.addAllV(
                mkItemPickGamebootImage(),
                mkItemPickGamebootAudio(),
                mkItemResetGameboot()
            )
        }
    }

    private fun mkCategoryMenuSfxResources(): XmbSettingsCategory {
        return XmbSettingsCategory(
            vsh,
            "settings_theme_resource_menu_sfx",
            R.drawable.ic_component_audio,
            R.string.settings_theme_resource_menu_sfx_name,
            R.string.settings_theme_resource_menu_sfx_desc
        ).apply {
            content.addAllV(
                mkItemPickMenuSfx(SfxType.Selection),
                mkItemPickMenuSfx(SfxType.Confirm),
                mkItemPickMenuSfx(SfxType.Cancel),
                mkItemResetMenuSfx(SfxType.Selection),
                mkItemResetMenuSfx(SfxType.Confirm),
                mkItemResetMenuSfx(SfxType.Cancel)
            )
        }
    }

    private fun mkCategoryHudResources(): XmbSettingsCategory {
        return XmbSettingsCategory(
            vsh,
            "settings_theme_resource_hud",
            R.drawable.icon_battery,
            R.string.settings_theme_resource_hud_name,
            R.string.settings_theme_resource_hud_desc
        ).apply {
            content.addAllV(
                mkItemPickBatteryGlyph(),
                mkItemResetBatteryGlyph()
            )
        }
    }

    private fun mkCategoryResourceOverrides(): XmbSettingsCategory {
        return XmbSettingsCategory(
            vsh,
            "settings_theme_resource_overrides",
            R.drawable.category_setting,
            R.string.settings_theme_resource_overrides_name,
            R.string.settings_theme_resource_overrides_desc
        ).apply {
            content.addAllV(
                mkCategoryFontResources(),
                mkCategoryWallpaperResources(),
                mkCategoryColdbootResources(),
                mkCategoryGamebootResources(),
                mkCategoryMenuSfxResources(),
                mkCategoryHudResources(),
                mkItemCustomizeIcons()
            )
        }
    }

    private fun mkItemExportXtf(): XmbSettingsItem {
        return XmbSettingsItem(
            vsh,
            "settings_theme_export_xtf",
            R.string.settings_theme_export_xtf_name,
            R.string.settings_theme_export_xtf_desc,
            R.drawable.ic_open,
            { "" }
        ) {
            vsh.xmbView?.context?.xmb?.startActivityForResult(
                vsh.M.xtf.createExportIntent(),
                Vsh.ACT_REQ_EXPORT_XTF
            )
        }
    }

    private fun mkCategoryThemePackages(): XmbSettingsCategory {
        return XmbSettingsCategory(
            vsh,
            "settings_theme_packages",
            R.drawable.category_setting,
            R.string.settings_theme_packages_name,
            R.string.settings_theme_packages_desc
        ).apply {
            content.addAllV(
                mkItemImportXtf(),
                mkItemExportXtf()
            )
        }
    }

    private fun mkItemImportXtf(): XmbSettingsItem {
        return XmbSettingsItem(
            vsh,
            "settings_theme_import_xtf",
            R.string.settings_theme_import_xtf_name,
            R.string.settings_theme_import_xtf_desc,
            R.drawable.ic_open,
            { "" }
        ) {
            vsh.xmbView?.context?.xmb?.startActivityForResult(
                vsh.M.xtf.createImportIntent(),
                Vsh.ACT_REQ_IMPORT_XTF
            )
        }
    }

    private var pickingIconType = IconManager.IconType.Category
    private var pickingIconId = ""

    private fun mkItemCustomizeIcons(): XmbSettingsItem {
        return XmbSettingsItem(vsh, "settings_customize_shell_icons",
            R.string.settings_theme_customize_icons_name,
            R.string.settings_theme_customize_icons_desc,
            R.drawable.category_setting, { "" },
            {
                val menuItems = ArrayList<XmbMenuItem>()
                var order = 0

                // Icon Library Folder
                if(sdkAtLeast(21)) {
                    menuItems.add(XmbMenuItem.XmbMenuItemLambda({ vsh.getString(R.string.settings_theme_icon_library_folder) }, { false }, order++) {
                        (vsh.xmbView?.context as? Xmb)?.shellIconLibraryPicker?.launch(null)
                    })

                    menuItems.add(XmbMenuItem.XmbMenuItemLambda({ vsh.getString(R.string.settings_theme_icon_library_reset) }, { false }, order++) {
                        vsh.M.pref.set(PrefEntry.USER_ICON_LIBRARY_URI, "").push()
                        vsh.postNotification(R.drawable.category_setting, vsh.getString(R.string.common_success), "")
                    })

                    menuItems.add(XmbMenuItem.XmbMenuItemLambda({ "" }, { true }, order++) {})
                }

                // Categories
                menuItems.add(XmbMenuItem.XmbMenuItemLambda({ vsh.getString(R.string.settings_theme_category_icons) }, { true }, order++) {})
                getShellIconCategoryTargets().forEach { category ->
                    menuItems.add(XmbMenuItem.XmbMenuItemLambda({ category.displayName }, { false }, order++) {
                        pickingIconType = IconManager.IconType.Category
                        pickingIconId = category.id
                        vsh.xmbView?.widgets?.sideMenu?.show(mkSubMenuIcon(category.id, IconManager.IconType.Category))
                    })
                }

                // Nodes
                menuItems.add(XmbMenuItem.XmbMenuItemLambda({ vsh.getString(R.string.settings_theme_node_icons) }, { true }, order++) {})
                getShellIconNodeTargets().forEach { target ->
                    menuItems.add(XmbMenuItem.XmbMenuItemLambda({ "${target.categoryName} / ${target.node.displayName}" }, { false }, order++) {
                        pickingIconType = IconManager.IconType.Node
                        pickingIconId = target.node.id
                        vsh.xmbView?.widgets?.sideMenu?.show(mkSubMenuIcon(target.node.id, IconManager.IconType.Node))
                    })
                }

                // Settings
                menuItems.add(XmbMenuItem.XmbMenuItemLambda({ vsh.getString(R.string.category_settings) }, { true }, order++) {})
                getShellIconSettingsTargets().forEach { target ->
                    menuItems.add(XmbMenuItem.XmbMenuItemLambda({ target.displayName }, { false }, order++) {
                        pickingIconType = IconManager.IconType.Settings
                        pickingIconId = target.id
                        vsh.xmbView?.widgets?.sideMenu?.show(mkSubMenuIcon(target.id, IconManager.IconType.Settings))
                    })
                }

                // Reset All
                menuItems.add(XmbMenuItem.XmbMenuItemLambda({ vsh.getString(R.string.settings_theme_icon_reset_all) }, { false }, order++) {
                    vsh.M.iconManager.resetAllUserIcons()
                    vsh.postNotification(R.drawable.category_setting, vsh.getString(R.string.common_success), vsh.getString(R.string.settings_system_clear_cache_success))
                })

                vsh.xmbView?.widgets?.sideMenu?.show(menuItems)
            }
        )
    }

    private fun mkSubMenuIcon(id: String, type: IconManager.IconType): ArrayList<XmbMenuItem> {
        val items = ArrayList<XmbMenuItem>()
        items.add(XmbMenuItem.XmbMenuItemLambda({ vsh.getString(R.string.common_change) }, { false }, 0) {
            pickingIconType = type
            pickingIconId = id
            vsh.xmbView?.context?.xmb?.shellIconPicker?.launch("image/*")
        })
        items.add(XmbMenuItem.XmbMenuItemLambda({ vsh.getString(R.string.dlg_info_reset) }, { false }, 1) {
            vsh.M.iconManager.setUserIcon(type, id, null)
            vsh.postNotification(R.drawable.category_setting, vsh.getString(R.string.common_success), id)
        })
        return items
    }

    fun onIconPicked(uri: android.net.Uri) {
        val type = pickingIconType
        val id = pickingIconId

        try {
            val tempFile = java.io.File(vsh.cacheDir, "temp_icon_pick.png")
            vsh.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            vsh.M.iconManager.setUserIcon(type, id, tempFile)
            tempFile.delete()
            vsh.postNotification(R.drawable.category_setting, vsh.getString(R.string.common_success), id)
        } catch(e: Exception) {
            e.printStackTrace()
        }
    }

    fun onFontPicked(uri: android.net.Uri) {
        val result = vsh.M.font.importUserFont(uri)
        result.onSuccess { fontFile ->
            vsh.postNotification(R.drawable.category_setting, vsh.getString(R.string.common_success), fontFile.name)
            vsh.xmbView?.context?.xmb?.recreate()
        }.onFailure { error ->
            vsh.M.font.reloadActiveFont()
            vsh.postNotification(
                R.drawable.ic_error,
                vsh.getString(R.string.error_common_header),
                vsh.getString(R.string.font_load_failed, error.localizedMessage ?: error.javaClass.simpleName)
            )
        }
    }

    fun onColdbootImagePicked(uri: android.net.Uri) {
        vsh.M.customizer.importColdbootImage(uri)
            .onSuccess { file ->
                vsh.postNotification(
                    R.drawable.category_setting,
                    vsh.getString(R.string.common_success),
                    vsh.getString(R.string.settings_theme_coldboot_image_pick_success, file.name)
                )
            }
            .onFailure { error ->
                vsh.postNotification(
                    R.drawable.ic_error,
                    vsh.getString(R.string.error_common_header),
                    error.localizedMessage ?: error.javaClass.simpleName,
                    5.0f
                )
            }
    }

    fun onColdbootAudioPicked(uri: android.net.Uri) {
        vsh.M.customizer.importColdbootAudio(uri)
            .onSuccess { file ->
                vsh.postNotification(
                    R.drawable.category_setting,
                    vsh.getString(R.string.common_success),
                    vsh.getString(R.string.settings_theme_coldboot_audio_pick_success, file.name)
                )
            }
            .onFailure { error ->
                vsh.postNotification(
                    R.drawable.ic_error,
                    vsh.getString(R.string.error_common_header),
                    error.localizedMessage ?: error.javaClass.simpleName,
                    5.0f
                )
            }
    }

    fun onGamebootImagePicked(uri: android.net.Uri) {
        vsh.M.customizer.importGamebootImage(uri)
            .onSuccess { file ->
                vsh.postNotification(
                    R.drawable.category_setting,
                    vsh.getString(R.string.common_success),
                    vsh.getString(R.string.settings_theme_gameboot_image_pick_success, file.name)
                )
            }
            .onFailure { error ->
                vsh.postNotification(
                    R.drawable.ic_error,
                    vsh.getString(R.string.error_common_header),
                    error.localizedMessage ?: error.javaClass.simpleName,
                    5.0f
                )
            }
    }

    fun onGamebootAudioPicked(uri: android.net.Uri) {
        vsh.M.customizer.importGamebootAudio(uri)
            .onSuccess { file ->
                vsh.postNotification(
                    R.drawable.category_setting,
                    vsh.getString(R.string.common_success),
                    vsh.getString(R.string.settings_theme_gameboot_audio_pick_success, file.name)
                )
            }
            .onFailure { error ->
                vsh.postNotification(
                    R.drawable.ic_error,
                    vsh.getString(R.string.error_common_header),
                    error.localizedMessage ?: error.javaClass.simpleName,
                    5.0f
                )
            }
    }

    fun onBatteryGlyphPicked(uri: android.net.Uri) {
        vsh.M.customizer.importBatteryGlyph(uri)
            .onSuccess { file ->
                vsh.postNotification(
                    R.drawable.category_setting,
                    vsh.getString(R.string.common_success),
                    vsh.getString(R.string.settings_theme_battery_glyph_pick_success, file.name)
                )
            }
            .onFailure { error ->
                vsh.postNotification(
                    R.drawable.ic_error,
                    vsh.getString(R.string.error_common_header),
                    error.localizedMessage ?: error.javaClass.simpleName,
                    5.0f
                )
            }
    }

    override fun createCategory(): XmbSettingsCategory {
        return XmbSettingsCategory(vsh,
            "settings_category_theme",
            R.drawable.category_setting,
            R.string.settings_category_theme_name,
            R.string.settings_category_theme_desc
        ).apply {
            content.addAllV(
                M.settings.display.mkItemDisplayLayout(),
                M.settings.display.wave.createCategory(),
                M.settings.system.mkItemLegacyIconBg(),
                M.settings.system.mkItemWideIconCard(),
                mkItemThemeSelection(),
                mkCategoryResourceOverrides(),
                mkCategoryThemePackages()
            )
        }
    }
}
