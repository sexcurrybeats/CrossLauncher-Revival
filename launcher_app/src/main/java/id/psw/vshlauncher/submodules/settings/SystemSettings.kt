package id.psw.vshlauncher.submodules.settings

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import id.psw.vshlauncher.BuildConfig
import id.psw.vshlauncher.PrefEntry
import id.psw.vshlauncher.R
import id.psw.vshlauncher.SysBar
import id.psw.vshlauncher.Vsh
import id.psw.vshlauncher.addAllV
import id.psw.vshlauncher.postNotification
import id.psw.vshlauncher.select
import id.psw.vshlauncher.services.SystemNotificationListener
import id.psw.vshlauncher.submodules.PadKey
import id.psw.vshlauncher.submodules.SettingsSubmodule
import id.psw.vshlauncher.submodules.XmbAdaptiveIconRenderer
import id.psw.vshlauncher.types.CifLoader
import id.psw.vshlauncher.types.VideoIconMode
import id.psw.vshlauncher.types.items.XmbAppItem
import id.psw.vshlauncher.types.items.XmbMenuItem
import id.psw.vshlauncher.types.items.XmbNodeItem
import id.psw.vshlauncher.types.items.XmbSettingsCategory
import id.psw.vshlauncher.types.items.XmbSettingsItem
import id.psw.vshlauncher.views.dialogviews.AboutDeviceDialogView
import id.psw.vshlauncher.views.dialogviews.ArrangeCategoryDialogView
import id.psw.vshlauncher.views.dialogviews.ConfirmDialogView
import id.psw.vshlauncher.views.dialogviews.IconPriorityDialogView
import id.psw.vshlauncher.views.dialogviews.LegacyIconBackgroundDialogView
import id.psw.vshlauncher.views.dialogviews.LicenseDialogView
import id.psw.vshlauncher.views.dialogviews.TextDialogView
import id.psw.vshlauncher.views.widgets.XmbSideMenu
import id.psw.vshlauncher.xmb
import id.psw.vshlauncher.livewallpaper.XMBWaveSurfaceView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class SystemSettings(private val vsh: Vsh) : ISettingsCategories(vsh) {

    fun mkItemShowFps() : XmbSettingsItem {
        val text = {
            val strId = vsh.xmbView?.widgets?.debugInfo?.showLauncherFPS?.select(
                R.string.common_yes,
                R.string.common_no) ?: R.string.unknown
            vsh.getString(strId)
        }

        val click = {
            if(vsh.haveXmbView){
                val w = vsh.safeXmbView.widgets.debugInfo
                w.showLauncherFPS = !w.showLauncherFPS
                M.pref.set(PrefEntry.SHOW_LAUNCHER_FPS, w.showLauncherFPS.select(1,0))
            }
        }

        return XmbSettingsItem(vsh, "settings_system_show_fps",
            R.string.settings_system_show_fps_name,
            R.string.settings_system_show_fps_desc,
            R.drawable.category_setting,
            text, click)
    }

    fun mkItemDetailedMem() : XmbSettingsItem {
        val text = {
            val strId = vsh.xmbView?.widgets?.debugInfo?.showDetailedMemory?.select(
                R.string.common_yes,
                R.string.common_no
            ) ?: R.string.unknown
            vsh.getString(strId)
        }

        val click = {
            if (vsh.haveXmbView) {
                val w = vsh.safeXmbView.widgets.debugInfo
                w.showDetailedMemory = !w.showDetailedMemory
                M.pref.set(PrefEntry.SHOW_DETAILED_MEMORY, w.showDetailedMemory.select(1, 0))
            }
        }

        return XmbSettingsItem(
            vsh, "settings_system_show_detailed_mem",
            R.string.settings_system_detailed_mem_name,
            R.string.settings_system_detailed_mem_desc,
            R.drawable.icon_device_info,
            text, click
        )
    }

    private fun mkItemShowDebugSettings(): XmbSettingsItem {
        return XmbSettingsItem(vsh, "settings_system_show_debug_settings",
            R.string.settings_system_show_debug_settings_name,
            R.string.settings_system_show_debug_settings_desc,
            R.drawable.category_debug,
            {
                vsh.getString(
                    M.pref.get(PrefEntry.SHOW_DEBUG_SETTINGS, BuildConfig.DEBUG).select(
                        R.string.common_enabled,
                        R.string.common_disabled
                    )
                )
            }
        ) {
            val enabled = !M.pref.get(PrefEntry.SHOW_DEBUG_SETTINGS, BuildConfig.DEBUG)
            M.settings.setDebugSettingsVisible(enabled)
            vsh.postNotification(
                R.drawable.category_setting,
                vsh.getString(R.string.settings_system_show_debug_settings_name),
                vsh.getString(
                    enabled.select(
                        R.string.settings_system_show_debug_settings_enabled,
                        R.string.settings_system_show_debug_settings_disabled
                    )
                ),
                3.0f
            )
        }
    }

    private fun mkItemSystemArrange() : XmbSettingsItem {
        return XmbSettingsItem(
            vsh, "settings_system_rearrange",
            R.string.settings_system_rearrange_category_name,
            R.string.settings_system_rearrange_category_desc,
            R.drawable.category_setting, { "" }
        ){
            if(vsh.haveXmbView) vsh.safeXmbView.showDialog(ArrangeCategoryDialogView(vsh.safeXmbView))
        }
    }

    private fun mkItemReorderCategory() : XmbSettingsItem {
        return XmbSettingsItem(vsh, "settings_system_reorder_icon_loading",
            R.string.settings_system_reorder_icon_loading_name,
            R.string.settings_system_reorder_icon_loading_desc,
            R.drawable.icon_storage, {
                val i = XmbAdaptiveIconRenderer.getIconPriorityAt(0)
                val id = IconPriorityDialogView.iconTypeToStrId[i]
                vsh.getString(R.string.settings_system_reorder_icon_loading_value).format(vsh.getString(id))
            }
        ){
            if(vsh.haveXmbView) vsh.safeXmbView.showDialog(IconPriorityDialogView(vsh.safeXmbView))
        }
    }

    fun mkItemOrientation() : XmbSettingsItem {
        val text = {
            val xmb = vsh.xmbView?.context?.xmb
            vsh.getString(when(xmb?.requestedOrientation){
                ActivityInfo.SCREEN_ORIENTATION_USER -> R.string.orient_user
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE -> R.string.orient_landscape
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT -> R.string.orient_portrait
                else -> R.string.orient_unknown
            })
        }

        val click : () -> Unit = {
            val xmb = vsh.xmbView?.context?.xmb
            xmb?.requestedOrientation = when(xmb?.requestedOrientation){
                ActivityInfo.SCREEN_ORIENTATION_USER -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_USER
                else -> ActivityInfo.SCREEN_ORIENTATION_USER
            }
            M.pref.set(PrefEntry.DISPLAY_ORIENTATION, xmb?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_SENSOR)
        }

        val menu = { i : XmbSettingsItem ->
            val xmb = vsh.xmbView?.context?.xmb
            i.hasMenu = true
            val dMenuItems = arrayListOf<XmbMenuItem>()
            val arr = arrayOf(
                ActivityInfo.SCREEN_ORIENTATION_USER,
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            )
            arr.forEachIndexed { idx, it ->
                val nameStr = vsh.getString(when(it){
                    ActivityInfo.SCREEN_ORIENTATION_USER -> R.string.orient_user
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE -> R.string.orient_landscape
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT -> R.string.orient_portrait
                    else -> R.string.orient_unknown
                })
                dMenuItems.add(XmbMenuItem.XmbMenuItemLambda({nameStr}, {false}, idx){
                    xmb?.requestedOrientation = it
                })
            }
            i.menuItems = dMenuItems
        }

        return XmbSettingsItem(vsh, "settings_system_orientation",
            R.string.item_orientation,
            R.string.item_orientation_desc, R.drawable.icon_orientation, text, click).apply (menu)
    }

    private fun mkItemAsianConsole() : XmbSettingsItem {
        val text = {
            vsh.getString(
                if(PadKey.spotMarkedByX)
                    R.string.settings_system_asian_console_false
                else
                    R.string.settings_system_asian_console_true
            )
        }

        val click : () -> Unit = {
            PadKey.spotMarkedByX = !PadKey.spotMarkedByX
            M.pref.set(
                PrefEntry.CONFIRM_BUTTON,
                PadKey.spotMarkedByX.select(1,0))
        }
        return XmbSettingsItem(vsh, "settings_system_button",
            R.string.settings_system_asian_console_name, R.string.settings_system_asian_console_desc,
            R.drawable.category_games, text, click
        )
    }

    private fun mkItemDisableSplashMessage() : XmbSettingsItem {
        val text = {
            vsh.getString(vsh.xmbView?.screens?.coldBoot?.hideEpilepsyWarning?.select(
                R.string.common_yes, R.string.common_no
            ) ?: R.string.unknown)
        }

        val click = {
            val xv = vsh.xmbView
            if(xv != null){
                xv.screens.coldBoot.hideEpilepsyWarning = !xv.screens.coldBoot.hideEpilepsyWarning
                M.pref.set(
                    PrefEntry.DISABLE_EPILEPSY_WARNING,
                    xv.screens.coldBoot.hideEpilepsyWarning.select(1,0)
                )
            }
        }

        return XmbSettingsItem(vsh, "settings_system_epimsg_disable",
            R.string.settings_system_disable_splash_message_title,
            R.string.settings_system_disable_splash_message_desc,
            R.drawable.icon_info, text, click)
    }

    private fun mkItemVideoIconMode() : XmbSettingsItem {
        val kTypeVideoIconMode = mapOf(
            VideoIconMode.Disabled to R.string.system_video_mode_disabled,
            VideoIconMode.AllTime to R.string.system_video_mode_all_time,
            VideoIconMode.SelectedOnly to R.string.system_video_mode_selected_only
        )

        val text = {
            vsh.getString(kTypeVideoIconMode[CifLoader.videoIconMode] ?: R.string.unknown)
        }

        val click : () -> Unit = {
            CifLoader.videoIconMode = when(CifLoader.videoIconMode)
            {
                VideoIconMode.Disabled -> VideoIconMode.AllTime
                VideoIconMode.AllTime -> VideoIconMode.SelectedOnly
                VideoIconMode.SelectedOnly -> VideoIconMode.Disabled
            }
            M.pref.set(
                PrefEntry.VIDEO_ICON_PLAY_MODE,
                VideoIconMode.toInt(CifLoader.videoIconMode)
            )
        }

        val menu = { it : XmbSettingsItem ->
            it.hasMenu = true
            val menu = arrayListOf<XmbMenuItem>()
            var i = -(kTypeVideoIconMode.size / 2)
            for((k, m) in kTypeVideoIconMode){
                menu.add(XmbMenuItem.XmbMenuItemLambda( { vsh.getString(m) }, {false}, i++){
                    CifLoader.videoIconMode = k
                    M.pref.set(
                        PrefEntry.VIDEO_ICON_PLAY_MODE,
                        VideoIconMode.toInt(CifLoader.videoIconMode)
                    )
                    vsh.xmbView?.showSideMenu(false)
                })
            }
            it.menuItems= menu
        }

        return XmbSettingsItem(vsh, "settings_system_video_icon_mode",
            R.string.settings_system_video_icon_mode_name,
            R.string.settings_system_video_icon_mode_desc,
            R.drawable.category_video, text, click).apply (menu)
    }

    private fun mkItemAppDesc() : XmbSettingsItem {
        val kTypeAppDescKey = mapOf(
            XmbAppItem.DescriptionDisplay.None to R.string.settings_system_visible_desc_val_none,
            XmbAppItem.DescriptionDisplay.Date to R.string.settings_system_visible_desc_val_date,
            XmbAppItem.DescriptionDisplay.FileSize to R.string.settings_system_visible_desc_val_filesize,
            XmbAppItem.DescriptionDisplay.ModificationId to R.string.settings_system_visible_desc_val_modid,
            XmbAppItem.DescriptionDisplay.PackageName to R.string.settings_system_visible_desc_val_packagename,
            XmbAppItem.DescriptionDisplay.NkFileStyle to R.string.settings_system_visible_desc_val_nkfile
        )

        val text =
            {
                vsh.getString(
                    kTypeAppDescKey[XmbAppItem.descriptionDisplay] ?: R.string.settings_system_visible_desc_val_none
                )
            }

        val click : () -> Unit = {
            vsh.xmbView?.showSideMenu(true)
        }

        val menu = {it : XmbSettingsItem ->
            it.hasMenu = true
            val menu = arrayListOf<XmbMenuItem>()
            var i = -(kTypeAppDescKey.size / 2)
            for((k, m) in kTypeAppDescKey){
                menu.add(XmbMenuItem.XmbMenuItemLambda( { vsh.getString(m) }, {false}, i++){
                    XmbAppItem.descriptionDisplay = k
                    M.pref.set(PrefEntry.SYSTEM_VISIBLE_APP_DESC, k.ordinal)
                    vsh.xmbView?.showSideMenu(false)
                })
            }
            it.menuItems= menu
        }

        return XmbSettingsItem(vsh, "settings_system_visible_app_desc",
            R.string.settings_system_visible_app_desc_name,
            R.string.settings_system_visible_app_desc_desc,
            R.drawable.icon_info, text, click
        ).apply(menu)
    }

    private fun mkItemSkipGameBoot() : XmbSettingsItem {

        val text = {
            val i = vsh.xmbView?.screens?.gameBoot?.defaultSkip?.select(
                R.string.common_no,
                R.string.common_yes
            ) ?: R.string.empty_string
            vsh.getString(i)
        }

        val click : () -> Unit = {
            vsh.xmbView?.screens?.gameBoot?.defaultSkip = !(vsh.xmbView?.screens?.gameBoot?.defaultSkip ?: true)
            M.pref.set(
                PrefEntry.SKIP_GAMEBOOT,
                vsh.xmbView?.screens?.gameBoot?.defaultSkip?.select(1, 0) ?: 0
            )
        }

        return XmbSettingsItem(vsh, "settings_system_skip_gameboot",
            R.string.setting_show_gameboot_name,
            R.string.setting_show_gameboot_desc,
            R.drawable.icon_dynamic_theme_effect, text, click )
    }

    private fun mkItemShowHiddenApps() : XmbSettingsItem {
        val text = {
            vsh.getString(
                XmbAppItem.showHiddenByConfig.select(
                    R.string.common_yes,
                    R.string.common_no
                ))
        }

        val click = {
            XmbAppItem.showHiddenByConfig = !XmbAppItem.showHiddenByConfig
        }
        return XmbSettingsItem(vsh, "settings_system_show_hidden_app",
            R.string.settings_system_show_hidden_app_name,
            R.string.settings_system_show_hidden_app_desc,
            R.drawable.icon_hidden, text, click
        )
    }

    private fun mkItemPrioritizeTv() : XmbSettingsItem {
        val text =  {
            vsh.getString(vsh._prioritizeTvIntent.select(
                R.string.common_yes,
                R.string.common_no
            ))
        }
        val click : () -> Unit = {
            vsh._prioritizeTvIntent = !vsh._prioritizeTvIntent
            M.pref.set(PrefEntry.LAUNCHER_TV_INTENT_FIRST, vsh._prioritizeTvIntent.select(1, 0))
        }
        return XmbSettingsItem(vsh, "settings_system_prioritze_tv",
            R.string.settings_system_prioritize_tv_intent_name,
            R.string.settings_system_prioritize_tv_intent_desc,
            R.drawable.icon_video_anim_icon, text, click
        )

    }

    private fun mkItemBrowserLaunchMode(): XmbSettingsItem {
        val labelMap = linkedMapOf(
            0 to R.string.settings_system_browser_launch_mode_default,
            1 to R.string.settings_system_browser_launch_mode_ask
        )

        val item = XmbSettingsItem(vsh, "settings_system_browser_launch_mode",
            R.string.settings_system_browser_launch_mode_name,
            R.string.settings_system_browser_launch_mode_desc,
            R.drawable.icon_network,
            {
                val mode = M.pref.get(PrefEntry.INTERNET_BROWSER_LAUNCH_MODE, 0)
                vsh.getString(labelMap[mode] ?: R.string.unknown)
            }
        ) {
            vsh.xmbView?.showSideMenu(true)
        }

        item.hasMenu = true
        item.menuItems = arrayListOf<XmbMenuItem>().apply {
            labelMap.entries.forEachIndexed { index, (mode, label) ->
                add(XmbMenuItem.XmbMenuItemLambda({ vsh.getString(label) }, { false }, index) {
                    M.pref.set(PrefEntry.INTERNET_BROWSER_LAUNCH_MODE, mode)
                    vsh.xmbView?.showSideMenu(false)
                })
            }
        }

        return item
    }

    private fun mkItemSystemInfo() : XmbSettingsItem {
        val click = {
            if(vsh.haveXmbView) vsh.safeXmbView.showDialog(AboutDeviceDialogView(vsh.safeXmbView))
        }
        return XmbSettingsItem(vsh, "settings_system_info_dialog_open",
            R.string.setting_systeminfo_name,
            R.string.setting_systeminfo_desc,
            R.drawable.icon_device_info, { "" }, click)
    }

    private fun mkItemLicenses() : XmbSettingsItem {
        val click = {
            if(vsh.haveXmbView) vsh.safeXmbView.showDialog(LicenseDialogView(vsh.safeXmbView))
        }
        return XmbSettingsItem(vsh, "settings_license_dialog_open",
            R.string.setting_license_name,
            R.string.setting_license_desc,
            R.drawable.category_help, { "" }, click)

    }

    private fun mkItemRestartLauncher(): XmbSettingsItem {
        return XmbSettingsItem(vsh, "settings_system_restart_launcher",
            R.string.common_reboot,
            R.string.home_screen_reboot_desc,
            R.drawable.icon_refresh, { "" }
        ) {
            val xv = vsh.xmbView ?: return@XmbSettingsItem
            xv.showDialog(TextDialogView(xv)
                .setData(null, vsh.getString(R.string.reboot_dlg_confirm_title), vsh.getString(R.string.reboot_dlg_confirm_content))
                .setNegative(vsh.getString(android.R.string.cancel)) { it.finish(xv.screens.mainMenu) }
                .setPositive(vsh.getString(R.string.common_reboot)) { vsh.restart() })
        }
    }

    private fun setSysBarVisibility(i:Int){
        vsh.xmb.sysBarVisibility = i
        M.pref.set(PrefEntry.SYSTEM_STATUS_BAR, i)
        vsh.xmb.updateSystemBarVisibility()
    }

    fun mkItemAndroidSystemBar() : XmbSettingsItem {
        val text = {
            vsh.getString(when(vsh.xmb.sysBarVisibility){
                SysBar.ALL -> R.string.system_bar_visible_all
                SysBar.NAVIGATION -> R.string.system_bar_visible_navigation
                SysBar.STATUS -> R.string.system_bar_visible_status
                SysBar.NONE -> R.string.system_bar_visible_none
                else -> R.string.unknown
            })
        }

        val click : () -> Unit = {
            val i = when(vsh.xmb.sysBarVisibility){
                SysBar.ALL -> SysBar.STATUS
                SysBar.STATUS -> SysBar.NAVIGATION
                SysBar.NAVIGATION -> SysBar.NONE
                SysBar.NONE -> SysBar.ALL
                else -> SysBar.NONE
            }
            setSysBarVisibility(i)
        }

        val menu =  { item : XmbSettingsItem ->
            val menu = arrayListOf<XmbMenuItem>()
            arrayListOf(
                R.string.system_bar_visible_all to SysBar.ALL,
                R.string.system_bar_visible_navigation to SysBar.NAVIGATION,
                R.string.system_bar_visible_status to SysBar.STATUS,
                R.string.system_bar_visible_none to SysBar.NONE
            ).forEachIndexed { i, it ->
                menu.add(XmbMenuItem.XmbMenuItemLambda({ vsh.getString(it.first) }, { false }, i){
                    setSysBarVisibility(it.second)
                    vsh.xmb.xmbView.widgets.sideMenu.isDisplayed = false
                })
            }
            item.menuItems = menu
            item.hasMenu = true
        }

        return XmbSettingsItem(vsh, "settings_system_android_bar",
            R.string.settings_system_android_bar_name,
            R.string.settings_system_android_bar_desc,
            R.drawable.icon_hidden, text, click).apply(menu)
    }

    fun mkItemLegacyIconBg() : XmbSettingsItem {
        return XmbSettingsItem(vsh, "settings_system_legacy_icon_bg", R.string.dlg_legacyicon_title, R.string.settings_system_legacy_icon_background_desc, R.drawable.icon_video_anim_icon, {
            val mode = M.pref.get(PrefEntry.ICON_RENDERER_LEGACY_BACKGROUND, 0)
            vsh.getString(when(mode){
                1 -> R.string.common_enabled
                2 -> R.string.dlg_legacyicon_material_you
                else -> R.string.common_disabled
            })
        }){
            val xv = vsh.xmbView
            xv?.showDialog(LegacyIconBackgroundDialogView(xv))
        }
    }

    fun mkItemWideIconCard() : XmbSettingsItem {
        return XmbSettingsItem(vsh, "settings_system_wide_icon_card",
            R.string.settings_system_wide_icon_card_name,
            R.string.settings_system_wide_icon_card_desc,
            R.drawable.category_apps,
            {
                vsh.getString(
                    M.pref.get(PrefEntry.ICON_RENDERER_WIDE_CARD, true).select(
                        R.string.common_enabled,
                        R.string.common_disabled
                    )
                )
            }
        ) {
            val enabled = !M.pref.get(PrefEntry.ICON_RENDERER_WIDE_CARD, true)
            M.pref.set(PrefEntry.ICON_RENDERER_WIDE_CARD, enabled).push()
            vsh.M.icons.readPreferences()
            vsh.M.bmp.releaseAll()
        }
    }

    private fun mkItemSystemNotification(): XmbSettingsItem {
        return XmbSettingsItem(vsh, "settings_system_notification_enabled",
            R.string.settings_system_notification_enabled_name,
            R.string.settings_system_notification_enabled_desc,
            R.drawable.category_notifications,
            {
                val i = SystemNotificationListener.getIsAllowed(vsh).select(R.string.common_enabled, R.string.common_disabled)
                vsh.getString(i)
            }
        ){
            val i = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
            val key = ":settings:fragment_args_key"
            val component =SystemNotificationListener.componentName
            i.putExtra(key, component.flattenToString())
            i.putExtra(":settings:show_fragment_args", Bundle().apply {
                putString(key, component.flattenToString())
            })
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                vsh.startActivity(i)
            }catch(e:Exception){
                vsh.postNotification(R.drawable.category_notifications, vsh.getString(R.string.error_sysnotif_no_settings), e.localizedMessage ?: "No message", 5.0f)
            }
        }
    }

    private fun mkItemTrialMode(cat: XmbSettingsCategory){
        val cal = Calendar.getInstance()
        val mon = cal.get(Calendar.MONTH)
        val day = cal.get(Calendar.DAY_OF_MONTH)
        if(mon == 3 && day == 1){
            cat.content.add(XmbSettingsItem(vsh, "settings_what_is_it",
                R.string.settings_system_test_option_name,
                R.string.settings_system_test_option_desc,
                R.drawable.icon_developer, {
                    val i = vsh.xmbView?.keygenActive?.select(
                        R.string.settings_system_test_option_value_on,
                        R.string.settings_system_test_option_value_off) ?: R.string.empty_string
                    vsh.getString(i)
                }
            ){
                vsh.xmbView?.keygenActive = true
            })
        }
    }

    fun mkItemSideMenuNavi() : XmbSettingsItem {
        val kItems = arrayOf(
            R.string.settings_system_sidemenu_navi_mode_value_tap to XmbSideMenu.TouchInteractMode.Tap,
            R.string.settings_system_sidemenu_navi_mode_value_gesture to XmbSideMenu.TouchInteractMode.Gesture
        )

        val menuItems = arrayListOf<XmbMenuItem>()
        kItems.forEachIndexed { index, (str, sValue) ->
            menuItems.add(XmbMenuItem.XmbMenuItemLambda({ vsh.getString(str) }, { false }, index){
                vsh.xmbView?.widgets?.sideMenu?.interactionMode = sValue
                vsh.xmb.xmbView.widgets.sideMenu.isDisplayed = false
            })
        }

        val item = XmbSettingsItem(vsh, "settings_system_sidemenu_navi_mode",
            R.string.settings_system_sidemenu_navi_mode_name,
            R.string.settings_system_sidemenu_navi_mode_desc,
            R.drawable.category_games,
            {
                when(vsh.xmbView?.widgets?.sideMenu?.interactionMode)
                {
                    XmbSideMenu.TouchInteractMode.Tap -> vsh.getString(R.string.settings_system_sidemenu_navi_mode_value_tap)
                    XmbSideMenu.TouchInteractMode.Gesture -> vsh.getString(R.string.settings_system_sidemenu_navi_mode_value_gesture)
                    else -> vsh.getString(R.string.unknown)
                }
            },
        ){
            vsh.xmbView?.showSideMenu(true)
        }

        item.hasMenu = true
        item.menuItems = menuItems

        return item
    }

    private fun mkItemManageNodes(): XmbSettingsCategory {
        val cat = XmbSettingsCategory(vsh, "settings_system_manage_nodes",
            R.drawable.category_setting,
            R.string.settings_system_manage_nodes_name,
            R.string.settings_system_manage_nodes_desc
        )

        fun showBuiltInLaunchTargetMenu(node: XmbNodeItem, refreshNodes: () -> Unit) {
            val menu = arrayListOf<XmbMenuItem>()
            menu.add(XmbMenuItem.XmbMenuItemLambda({
                vsh.getString(R.string.settings_system_set_node_launch_app)
            }, { false }, 0) {
                val apps = vsh.allAppEntries.toList().sortedBy { it.displayName.lowercase() }
                if (apps.isEmpty()) {
                    vsh.postNotification(
                        R.drawable.category_setting,
                        vsh.getString(R.string.settings_system_set_node_launch_app),
                        vsh.getString(R.string.settings_system_launch_app_list_empty)
                    )
                    vsh.M.apps.reloadAppList()
                    return@XmbMenuItemLambda
                }

                val appMenu = arrayListOf<XmbMenuItem>()
                apps.forEachIndexed { index, app ->
                    appMenu.add(XmbMenuItem.XmbMenuItemLambda({ app.displayName }, { false }, index) {
                        vsh.setBuiltInNodeAppLaunchOverride(
                            node.id,
                            app.resInfo.activityInfo.packageName,
                            app.displayName
                        )
                        vsh.xmbView?.showSideMenu(false)
                        refreshNodes()
                    })
                }
                vsh.xmbView?.showSideMenu(appMenu)
            })

            menu.add(XmbMenuItem.XmbMenuItemLambda({
                vsh.getString(R.string.settings_system_set_node_launch_url)
            }, { false }, 1) {
                vsh.xmbView?.showSideMenu(false)
                id.psw.vshlauncher.views.nativedlg.NativeEditTextDialog(vsh)
                    .setTitle(vsh.getString(R.string.settings_system_launch_item_target))
                    .setOnFinish { target ->
                        if (target.isNotBlank()) {
                            vsh.setBuiltInNodeUrlLaunchOverride(node.id, target)
                            refreshNodes()
                        }
                    }.show()
            })

            if (vsh.getBuiltInNodeLaunchOverride(node.id) != null) {
                menu.add(XmbMenuItem.XmbMenuItemLambda({
                    vsh.getString(R.string.settings_system_reset_node_launch_target)
                }, { false }, 2) {
                    vsh.resetBuiltInNodeLaunchOverride(node.id)
                    vsh.xmbView?.showSideMenu(false)
                    refreshNodes()
                })
            }

            vsh.xmbView?.showSideMenu(menu)
        }

        fun refreshNodes() {
            cat.content.clear()
            val createItems = XmbSettingsCategory(vsh, "settings_system_manage_create",
                R.drawable.category_setting,
                R.string.settings_system_manage_create_name,
                R.string.settings_system_manage_create_desc
            )
            val customFolders = XmbSettingsCategory(vsh, "settings_system_manage_custom_folders",
                R.drawable.category_apps,
                R.string.settings_system_manage_custom_folders_name,
                R.string.settings_system_manage_custom_folders_desc
            )
            val launchItems = XmbSettingsCategory(vsh, "settings_system_manage_launch_items",
                R.drawable.icon_network,
                R.string.settings_system_manage_launch_items_name,
                R.string.settings_system_manage_launch_items_desc
            )
            val builtInNodes = XmbSettingsCategory(vsh, "settings_system_manage_builtin_nodes",
                R.drawable.icon_account,
                R.string.settings_system_manage_builtin_nodes_name,
                R.string.settings_system_manage_builtin_nodes_desc
            )

            createItems.content.add(XmbSettingsItem(vsh, "settings_system_add_node",
                R.string.settings_system_add_node, R.string.empty_string,
                R.drawable.category_apps, { "" }
            ) {
                // Show Add Node Flow
                val menu = arrayListOf<XmbMenuItem>()
                vsh.categories.forEachIndexed { index, category ->
                    menu.add(XmbMenuItem.XmbMenuItemLambda({ category.displayName }, { false }, index) {
                        vsh.xmbView?.showSideMenu(false)
                        id.psw.vshlauncher.views.nativedlg.NativeEditTextDialog(vsh)
                            .setTitle(vsh.getString(R.string.settings_system_node_name))
                            .setOnFinish { name ->
                                if (name.isNotEmpty()) {
                                    val nodeId = "custom_node_" + name.lowercase().replace(" ", "_")
                                    vsh.addCustomNode(category.id, nodeId, name)
                                    refreshNodes()
                                }
                            }.show()
                    })
                }
                vsh.xmbView?.showSideMenu(menu)
            })

            createItems.content.add(XmbSettingsItem(vsh, "settings_system_add_url_launch_item",
                R.string.settings_system_add_url_launch_item, R.string.empty_string,
                R.drawable.icon_network, { "" }
            ) {
                val menu = arrayListOf<XmbMenuItem>()
                vsh.categories.forEachIndexed { index, category ->
                    menu.add(XmbMenuItem.XmbMenuItemLambda({ category.displayName }, { false }, index) {
                        vsh.xmbView?.showSideMenu(false)
                        id.psw.vshlauncher.views.nativedlg.NativeEditTextDialog(vsh)
                            .setTitle(vsh.getString(R.string.settings_system_launch_item_name))
                            .setOnFinish { name ->
                                if (name.isNotBlank()) {
                                    id.psw.vshlauncher.views.nativedlg.NativeEditTextDialog(vsh)
                                        .setTitle(vsh.getString(R.string.settings_system_launch_item_target))
                                        .setOnFinish { target ->
                                            if (target.isNotBlank()) {
                                                vsh.addCustomUrlLaunchItem(category.id, name, target)
                                                refreshNodes()
                                            }
                                        }.show()
                                }
                            }.show()
                    })
                }
                vsh.xmbView?.showSideMenu(menu)
            })

            createItems.content.add(XmbSettingsItem(vsh, "settings_system_add_file_launch_item",
                R.string.settings_system_add_file_launch_item, R.string.empty_string,
                R.drawable.ic_folder, { "" }
            ) {
                id.psw.vshlauncher.views.nativedlg.NativeEditTextDialog(vsh)
                    .setTitle(vsh.getString(R.string.settings_system_launch_item_name))
                    .setOnFinish { name ->
                        if (name.isNotBlank()) {
                            vsh.startCustomFileLaunchItemPicker(Vsh.ITEM_CATEGORY_GAME, name)
                        }
                    }.show()
            })

            val customData = M.pref.get(id.psw.vshlauncher.PrefEntry.CUSTOM_NODES, "")
            if (customData.isNotEmpty()) {
                customData.split("|").forEach { nodeData ->
                    val parts = nodeData.split(":")
                    if (parts.size >= 3) {
                        val categoryId = parts[0]
                        val nodeId = parts[1]
                        val nodeName = parts[2]
                        customFolders.content.add(XmbSettingsItem(vsh, "node_$nodeId",
                            { nodeName }, { vsh.categories.find { it.id == categoryId }?.displayName ?: vsh.getString(R.string.unknown) },
                            R.drawable.category_apps, { "" }
                        ) {
                            // Delete Confirm
                            vsh.xmbView?.showDialog(id.psw.vshlauncher.views.dialogviews.ConfirmDialogView(vsh.safeXmbView,
                                nodeName, R.drawable.category_setting, vsh.getString(R.string.settings_system_node_delete_confirm)) { confirm ->
                                if (confirm) {
                                    vsh.removeCustomNode(categoryId, nodeId)
                                    refreshNodes()
                                }
                            })
                        })
                    }
                }
            }

            vsh.getCustomLaunchItemRecords().forEach { record ->
                val categoryName = vsh.categories.find { it.id == record.categoryId }?.displayName
                    ?: vsh.getString(R.string.unknown)
                launchItems.content.add(XmbSettingsItem(vsh, "custom_launch_${record.itemId}",
                    { record.title }, { categoryName },
                    if (record.type == "file") R.drawable.ic_folder else R.drawable.icon_network,
                    {
                        when {
                            record.type == "file" && record.packageName.isNotBlank() -> record.packageName
                            record.type == "file" -> record.fileUri
                            else -> record.target
                        }
                    }
                ) {
                    vsh.xmbView?.showDialog(id.psw.vshlauncher.views.dialogviews.ConfirmDialogView(
                        vsh.safeXmbView,
                        record.title,
                        R.drawable.icon_network,
                        vsh.getString(R.string.settings_system_launch_item_delete_confirm)
                    ) { confirm ->
                        if (confirm) {
                            vsh.removeCustomLaunchItem(record.itemId)
                            refreshNodes()
                        }
                    })
                })
            }

            vsh.categories.forEach { category ->
                category.content.filterIsInstance<XmbNodeItem>()
                    .filter { vsh.isBuiltInLaunchTargetConfigurable(it.id) && !it.builtInDefault }
                    .forEach { node ->
                        builtInNodes.content.add(XmbSettingsItem(vsh, "builtin_launch_${node.id}",
                            { node.displayName }, { category.displayName },
                            R.drawable.icon_network, {
                                vsh.getBuiltInNodeLaunchOverride(node.id)?.label
                                    ?: vsh.getString(R.string.settings_system_default_node_launch_target)
                            }
                        ) {
                            showBuiltInLaunchTargetMenu(node, ::refreshNodes)
                        })
                    }
            }

            vsh.categories.forEach { category ->
                category.content.filterIsInstance<XmbNodeItem>()
                    .filter { it.builtInDefault && (it.userHideable || it.userDeletable) }
                    .forEach { node ->
                        val ownerCategoryId = node.builtInCategoryId.ifBlank { category.id }
                        val ownerCategory = vsh.categories.find { it.id == ownerCategoryId } ?: category
                        builtInNodes.content.add(XmbSettingsItem(vsh, "builtin_node_${node.id}",
                            { node.displayName }, { ownerCategory.displayName },
                            R.drawable.icon_account, {
                                vsh.getString(
                                    if (vsh.isBuiltInNodeEffectivelyHidden(node.id)) {
                                        R.string.common_hidden
                                    } else {
                                        R.string.common_visible
                                    }
                                )
                            }
                        ) {
                            val menu = arrayListOf<XmbMenuItem>()
                            val isDefaultHiddenPlaceholder =
                                vsh.isBuiltInNodeDefaultHiddenUntilConfigured(node.id) &&
                                    vsh.getBuiltInNodeLaunchOverride(node.id) == null
                            if (node.userHideable && !isDefaultHiddenPlaceholder) {
                                menu.add(XmbMenuItem.XmbMenuItemLambda({
                                    vsh.getString(
                                        if (vsh.isBuiltInNodeHidden(node.id)) {
                                            R.string.common_show
                                        } else {
                                            R.string.common_hide
                                        }
                                    )
                                }, { false }, 0) {
                                    vsh.setBuiltInNodeHidden(node.id, !vsh.isBuiltInNodeHidden(node.id))
                                    vsh.xmbView?.showSideMenu(false)
                                    refreshNodes()
                                })
                            }
                            if (node.userDeletable) {
                                menu.add(XmbMenuItem.XmbMenuItemLambda({ vsh.getString(R.string.common_delete) }, { false }, 2) {
                                    vsh.xmbView?.showSideMenu(false)
                                    vsh.xmbView?.showDialog(id.psw.vshlauncher.views.dialogviews.ConfirmDialogView(
                                        vsh.safeXmbView,
                                        node.displayName,
                                        R.drawable.category_setting,
                                        vsh.getString(R.string.settings_system_builtin_node_delete_confirm)
                                    ) { confirm ->
                                        if (confirm) {
                                            vsh.deleteBuiltInDefaultNode(ownerCategory.id, node.id)
                                            refreshNodes()
                                        }
                                    })
                                })
                            }
                            if (vsh.isBuiltInLaunchTargetConfigurable(node.id)) {
                                menu.add(XmbMenuItem.XmbMenuItemLambda({
                                    vsh.getString(R.string.settings_system_edit_node_launch_target)
                                }, { false }, 1) {
                                    showBuiltInLaunchTargetMenu(node, ::refreshNodes)
                                })
                            }
                            vsh.xmbView?.showSideMenu(menu)
                        })
                    }
            }

            cat.content.add(createItems)
            if (customFolders.content.isNotEmpty()) cat.content.add(customFolders)
            if (launchItems.content.isNotEmpty()) cat.content.add(launchItems)
            if (builtInNodes.content.isNotEmpty()) cat.content.add(builtInNodes)
        }

        refreshNodes()
        return cat
    }

    private fun mkItemRebuildApp(): XmbSettingsItem {
        return XmbSettingsItem(vsh, "settings_system_rebuild_app",
            R.string.settings_system_rebuild_app_name,
            R.string.settings_system_rebuild_app_desc,
            R.drawable.category_setting, { "" }
        ) {
            vsh.M.apps.reloadAppList()
            vsh.postNotification(R.drawable.category_setting,
                vsh.getString(R.string.settings_system_rebuild_app_name),
                vsh.getString(R.string.common_success))
        }
    }

    private fun mkItemRefreshMedia(): XmbSettingsItem {
        return XmbSettingsItem(vsh, "settings_system_refresh_media",
            R.string.settings_system_refresh_media_name,
            R.string.settings_system_refresh_media_desc,
            R.drawable.category_setting, { "" }
        ) {
            vsh.M.media.mediaListingStart()
            vsh.postNotification(R.drawable.category_setting,
                vsh.getString(R.string.settings_system_refresh_media_name),
                vsh.getString(R.string.common_success))
        }
    }

    private fun mkItemClearCache(): XmbSettingsItem {
        return XmbSettingsItem(vsh, "settings_system_clear_cache",
            R.string.settings_system_clear_cache_name,
            R.string.settings_system_clear_cache_desc,
            R.drawable.category_setting, { "" }
        ) {
            vsh.M.bmp.releaseAll()
            vsh.postNotification(R.drawable.category_setting,
                vsh.getString(R.string.settings_system_clear_cache_name),
                vsh.getString(R.string.settings_system_clear_cache_success))
        }
    }

    private fun mkItemResetAllCustomizations(): XmbSettingsItem {
        return XmbSettingsItem(vsh, "settings_system_reset_all_customizations",
            R.string.settings_system_reset_all_customizations_name,
            R.string.settings_system_reset_all_customizations_desc,
            R.drawable.category_setting, { "" }
        ) {
            val xv = vsh.xmbView ?: return@XmbSettingsItem
            xv.showDialog(ConfirmDialogView(
                xv,
                vsh.getString(R.string.settings_system_reset_all_customizations_name),
                R.drawable.category_setting,
                vsh.getString(R.string.settings_system_reset_all_customizations_confirm)
            ) { confirmed ->
                if (!confirmed) return@ConfirmDialogView
                vsh.lifeScope.launch(Dispatchers.IO) {
                    val result = vsh.M.customizer.resetAllCustomizationsForFreshInstall()
                    vsh.M.pref.clearAllNow()
                    // Full reset should return to the intended public-safe shell defaults.
                    vsh.M.pref
                        .set(PrefEntry.MENU_LAYOUT, 1)
                        .set(PrefEntry.CONFIRM_BUTTON, 1)
                        .set(PrefEntry.USES_INTERNAL_WAVE_LAYER, true)
                    // Full reset should land on the public-safe default shell wording.
                    vsh.M.pref.set(PrefEntry.STORAGE_ROOT_LABEL_STYLE, 0).push()
                    val wavePrefs = vsh.getSharedPreferences(XMBWaveSurfaceView.PREF_NAME, Context.MODE_PRIVATE)
                    wavePrefs
                        .edit()
                        .clear()
                        .commit()
                    WaveSettings.applyFreshInstallDefaults(wavePrefs)

                    withContext(Dispatchers.Main) {
                        vsh.xmbView?.showSideMenu(false)
                        vsh.M.font.reloadActiveFont()
                        vsh.M.customizer.refreshThemes()
                        vsh.M.customizer.loadActiveTheme()
                        vsh.M.customizer.loadActiveLauncherWallpaper()
                        vsh.xmbView?.screens?.coldBoot?.reloadResources()
                        vsh.xmbView?.screens?.gameBoot?.reloadResources()
                        vsh.xmbView?.widgets?.statusBar?.reloadBatteryGlyphOverride()
                        vsh.M.audio.loadSfxData(false)
                        vsh.M.bmp.releaseAll()
                        vsh.M.apps.reloadAppList()
                        vsh.postNotification(
                            R.drawable.category_setting,
                            vsh.getString(R.string.settings_system_reset_all_customizations_name),
                            vsh.getString(R.string.settings_system_reset_all_customizations_success, result.deletedEntries),
                            5.0f
                        )
                        delay(750)
                        vsh.restart()
                    }
                }
            })
        }
    }

    private fun mkItemMaintenanceSubMenu(): XmbSettingsCategory {
        return XmbSettingsCategory(vsh, "settings_system_maintenance",
            R.drawable.category_setting,
            R.string.settings_system_maintenance_name,
            R.string.settings_system_maintenance_desc
        ).apply {
            content.addAllV(
                mkItemRebuildApp(),
                mkItemRefreshMedia(),
                mkItemClearCache(),
                vsh.M.settings.rootDir.settingsAddInstallPackage(),
                mkItemResetAllCustomizations(),
                mkItemResetAppCategories()
            )
        }
    }

    private fun mkItemResetAppCategories(): XmbSettingsItem {
        return XmbSettingsItem(vsh, "settings_system_reset_categories",
            R.string.settings_system_reset_categories_name,
            R.string.settings_system_reset_categories_desc,
            R.drawable.category_setting, { "" }
        ) {
            vsh.lifeScope.launch(Dispatchers.IO) {
                val customNodes = vsh.M.pref.get(PrefEntry.CUSTOM_NODES, "")
                val customNodeMap = customNodes.split("|")
                    .mapNotNull { nodeData ->
                        val parts = nodeData.split(":")
                        if (parts.size >= 3) parts[0] to parts[1] else null
                    }
                    .groupBy({ it.first }, { it.second })

                withContext(Dispatchers.Main) {
                    vsh.backStep()
                    vsh.xmbView?.showSideMenu(false)
                }

                vsh.allAppEntries.toList().forEach {
                    it.appCategory = ""
                    it.appNodeOverride = ""
                }

                vsh.M.pref.set(PrefEntry.CUSTOM_NODES, "").push()

                withContext(Dispatchers.Main) {
                    customNodeMap.forEach { (categoryId, nodeIds) ->
                        vsh.categories.find { it.id == categoryId }?.content?.removeAll { item ->
                            item is XmbNodeItem && item.id in nodeIds
                        }
                    }
                }

                vsh.M.apps.reloadAppList()
                vsh.postNotification(R.drawable.category_setting,
                    vsh.getString(R.string.settings_system_reset_categories_name),
                    vsh.getString(R.string.settings_system_reset_categories_success))
            }
        }
    }

    override fun createCategory() : XmbSettingsCategory{
        return XmbSettingsCategory(vsh,
            "settings_category_system",
            R.drawable.category_setting,
            R.string.settings_category_system_name,
            R.string.settings_category_system_desc
        ).apply {
            content.addAllV(
                mkItemManageNodes(),
                mkItemSystemArrange(),
                mkItemMaintenanceSubMenu(),
                mkItemShowDebugSettings(),
                mkItemReorderCategory(),
                mkItemAsianConsole(),
                mkItemSystemNotification(),
                mkItemDisableSplashMessage(),
                mkItemVideoIconMode(),
                mkItemAppDesc(),
                mkItemSkipGameBoot(),
                mkItemShowHiddenApps(),
                mkItemPrioritizeTv(),
                mkItemBrowserLaunchMode(),
                mkItemSystemInfo(),
                mkItemLicenses(),
                mkItemRestartLauncher()
            )
            mkItemTrialMode(this)

        }
    }
}
