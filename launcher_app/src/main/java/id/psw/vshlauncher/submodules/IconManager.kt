package id.psw.vshlauncher.submodules

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.net.Uri
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.documentfile.provider.DocumentFile
import id.psw.vshlauncher.Logger
import id.psw.vshlauncher.PrefEntry
import id.psw.vshlauncher.R
import id.psw.vshlauncher.Vsh
import id.psw.vshlauncher.types.XmbItem
import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream

class IconManager(private val vsh: Vsh) : IVshSubmodule {
    override fun onCreate() {}
    override fun onDestroy() {}

    enum class IconType {
        Category,
        Node,
        Settings
    }

    companion object {
        const val TAG = "IconManager"
        const val ASSET_BASE_PATH = "default_psp_icons"
        const val OG_ASSET_BASE_PATH = "og icons"
        const val CATEGORY_PATH = "$ASSET_BASE_PATH/Catergory Icons"
        const val NODE_PATH = "$ASSET_BASE_PATH/First Level Icons"
        const val SETTINGS_PATH = "$ASSET_BASE_PATH/Supplemental/Settings second Level Icons"
        const val UTILITY_PATH = "$ASSET_BASE_PATH/Supplemental/Utility Icons"

        const val CUSTOM_ICON_DIR = "custom_icons"
        const val CUSTOM_CATEGORY_DIR = "categories"
        const val CUSTOM_NODE_DIR = "nodes"
        const val CUSTOM_SETTINGS_DIR = "settings"
    }

    private val categoryMappings = mapOf(
        Vsh.ITEM_CATEGORY_SETTINGS to "system.png",
        Vsh.ITEM_CATEGORY_PHOTO to "photo.png",
        Vsh.ITEM_CATEGORY_MUSIC to "music.png",
        Vsh.ITEM_CATEGORY_VIDEO to "video.png",
        Vsh.ITEM_CATEGORY_GAME to "game.png",
        Vsh.ITEM_CATEGORY_NETWORK to "network.png",
        Vsh.ITEM_CATEGORY_PSN to "psn.png"
    )

    private val ogCategoryMappings = mapOf(
        Vsh.ITEM_CATEGORY_SETTINGS to "tex_system.png",
        Vsh.ITEM_CATEGORY_PHOTO to "tex_photo.png",
        Vsh.ITEM_CATEGORY_MUSIC to "tex_music.png",
        Vsh.ITEM_CATEGORY_VIDEO to "tex_video.png",
        Vsh.ITEM_CATEGORY_GAME to "tex_game.png",
        Vsh.ITEM_CATEGORY_NETWORK to "tex_network.png",
        Vsh.ITEM_CATEGORY_PSN to "tex_psn.png"
    )

    private val nodeMappings = mapOf(
        "photo_camera" to "Camera - Body.png",
        "photo_ms" to "Memory Stick™ - Body.png",
        "music_ms" to "Memory Stick™ - Body.png",
        "video_ms" to "Memory Stick™ - Body.png",
        "game_ms" to "Memory Stick™ - Body.png",
        Vsh.NODE_APPS_MEMORY_STICK to "Memory Stick™ - Body.png",
        "game_saved_data" to "Saved Data Utility - Body.png",
        "game_android" to "Game Sharing - Body.png",
        "network_browser" to "Internet Browser - Body.png",
        "network_search" to "Internet Search - Body.png",
        Vsh.NODE_NETWORK_ONLINE_MANUALS to "Online Instruction Manuals - Body.png",
        Vsh.NODE_NETWORK_REMOTE_PLAY to "Remote Play - Body.png",
        Vsh.NODE_NETWORK_INTERNET_RADIO to "Internet Radio - Body.png",
        Vsh.NODE_NETWORK_RSS_CHANNEL to "RSS Channel - Body.png",
        Vsh.NODE_PSN_ACCOUNT_MANAGEMENT to "Account Management - Body.png",
        Vsh.NODE_PSN_STORE to "PlayStation®Store - Body.png"
    )

    private val ogNodeMappings = mapOf(
        "photo_camera" to "tex_camera.png",
        "photo_ms" to "tex_ms.png",
        "music_ms" to "tex_ms.png",
        "video_ms" to "tex_ms.png",
        "game_ms" to "tex_ms.png",
        Vsh.NODE_APPS_MEMORY_STICK to "tex_ms.png",
        "game_saved_data" to "tex_savedata.png",
        "game_android" to "tex_sharing.png",
        "network_browser" to "tex_browser.png",
        "network_search" to "tex_search.png",
        Vsh.NODE_NETWORK_ONLINE_MANUALS to "tex_help.png",
        Vsh.NODE_NETWORK_REMOTE_PLAY to "tex_premo.png",
        Vsh.NODE_NETWORK_INTERNET_RADIO to "tex_radio.png",
        Vsh.NODE_NETWORK_RSS_CHANNEL to "tex_cnf_rss.png",
        Vsh.NODE_PSN_ACCOUNT_MANAGEMENT to "tex_account.png",
        Vsh.NODE_PSN_STORE to "tex_psstore.png"
    )

    private val settingsMappings = mapOf(
        "settings_system_update" to "tex_cnf_update.32bit.png",
        "settings_install_package" to "tex_savedata.32bit.png",
        "settings_wave_set" to "tex_cnf_theme.32bit.png",
        "settings_wave_theme" to "tex_cnf_theme.32bit.png",
        "settings_wave_make_internal" to "tex_cnf_theme.32bit.png",
        "settings_wave_color_mode" to "tex_cnf_theme.32bit.png",
        "settings_wave_bg_preset" to "tex_cnf_theme.32bit.png",
        "settings_theme_selection" to "tex_cnf_theme.32bit.png",
        "settings_theme_custom_font_pick" to "tex_cnf_theme.32bit.png",
        "settings_theme_custom_font_reset" to "tex_cnf_theme.32bit.png",
        "settings_theme_reset_launcher_wallpaper" to "tex_cnf_theme.32bit.png",
        "settings_theme_export_xtf" to "tex_cnf_theme.32bit.png",
        "settings_theme_import_xtf" to "tex_cnf_theme.32bit.png",
        "settings_customize_shell_icons" to "tex_cnf_theme.32bit.png",
        "settings_system_orientation" to "tex_cnf_psp.32bit.png",
        "settings_system_button" to "tex_cnf_usb.32bit.png",
        "settings_system_video_icon_mode" to "tex_cnf_video.32bit.png",
        "settings_system_visible_app_desc" to "tex_cnf_photo.32bit.png",
        "settings_system_skip_gameboot" to "tex_cnf_update.32bit.png",
        "settings_system_show_hidden_app" to "tex_cnf_photo.32bit.png",
        "settings_system_prioritze_tv" to "tex_tv.32bit.png",
        "settings_system_browser_launch_mode" to "tex_browser.32bit.png",
        "settings_system_info_dialog_open" to "tex_infomation.32bit.png",
        "settings_license_dialog_open" to "tex_infomation.32bit.png",
        "settings_system_android_bar" to "tex_cnf_display.32bit.png",
        "settings_system_legacy_icon_bg" to "tex_cnf_photo.32bit.png",
        "settings_system_wide_icon_card" to "tex_cnf_photo.32bit.png",
        "settings_system_notification_enabled" to "tex_infomation.32bit.png",
        "settings_system_add_node" to "tex_cnf_psp.32bit.png",
        "settings_system_sidemenu_navi_mode" to "tex_cnf_psp.32bit.png",
        "settings_media_request_permission" to "tex_cnf_video.32bit.png",
        "settings_display_hide_bar" to "tex_cnf_video.32bit.png",
        "settings_display_bg_dim" to "tex_cnf_video.32bit.png",
        "settings_display_statusbar_fmt" to "tex_cnf_video.32bit.png",
        "settings_display_button_type" to "tex_cnf_video.32bit.png",
        "settings_display_analog_second" to "tex_cnf_video.32bit.png",
        "settings_display_operator" to "tex_cnf_video.32bit.png",
        "settings_screen_reference_size" to "tex_cnf_video.32bit.png",
        "audio_volume_master" to "tex_cnf_sound.32bit.png",
        "audio_volume_menubgm" to "tex_cnf_sound.32bit.png",
        "audio_volume_bgm" to "tex_cnf_sound.32bit.png",
        "audio_volume_sysbgm" to "tex_cnf_sound.32bit.png",
        "audio_volume_sfx" to "tex_cnf_sound.32bit.png",
        "audio_reload_sfx" to "tex_cnf_sound.32bit.png",
        "audio_pick_menu_bgm" to "tex_cnf_sound.32bit.png",
        "audio_delete_menu_bgm" to "tex_cnf_sound.32bit.png",
        "settings_system_language" to "tex_system.32bit.png",
        "settings_system_password" to "tex_cnf_password.32bit.png",
        "settings_system_date_time" to "tex_cnf_date.32bit.png",
        "settings_network_settings" to "tex_cnf_network.32bit.png",
        "settings_power_save" to "tex_cnf_save_energy.32bit.png",
        "settings_system_rebuild_app" to "tex_update.32bit.png",
        "settings_system_maintenance" to "tex_savedata.32bit.png",
        "settings_system_clear_cache" to "tex_cnf_psp.32bit.png",
        "settings_system_refresh_media" to "tex_cnf_video.32bit.png",
        "settings_system_reset_categories" to "tex_cnf_psp.32bit.png",
        "settings_category_display" to "tex_cnf_video.32bit.png",
        "settings_category_theme" to "tex_cnf_theme.32bit.png",
        "settings_category_audio" to "tex_cnf_sound.32bit.png",
        "settings_category_media" to "tex_cnf_video.32bit.png",
        "settings_category_system" to "tex_system.32bit.png",
        "settings_category_android" to "tex_cnf_usb.32bit.png",
        "settings_category_debug" to "tex_cnf_update.32bit.png"
    )

    fun getCategoryIcon(id: String, fallbackRes: Int): BitmapRef {
        return BitmapRef("bundled://category/$id", {
            loadResolvedIcon(id, IconType.Category, fallbackRes)
        })
    }

    fun getNodeIcon(id: String, fallbackRes: Int): BitmapRef {
        return BitmapRef("bundled://node/$id", {
            loadResolvedIcon(id, IconType.Node, fallbackRes)
        })
    }

    fun getSettingsIcon(id: String, fallbackRes: Int): BitmapRef {
        return BitmapRef("bundled://settings/$id", {
            loadResolvedIcon(id, IconType.Settings, fallbackRes)
        })
    }

    private fun loadResolvedIcon(id: String, type: IconType, fallbackRes: Int): Bitmap {
        loadUserIconBitmap(type, id)?.let { return it }
        loadThemeIconBitmap(type, id)?.let { return it }
        return applyDefaultShellShadow(loadIcon(id, type, fallbackRes))
    }

    private fun loadUserIconBitmap(type: IconType, id: String): Bitmap? {
        val subDir = subDirForType(type)

        // 1a. Internal Private Storage (Priority)
        val file = File(vsh.getExternalFilesDir(null), "$CUSTOM_ICON_DIR/$subDir/$id.png")
        if(file.exists()) {
            return try {
                BitmapFactory.decodeFile(file.absolutePath)?.let(::createFixedSizeBitmap)
            } catch(e: Exception) {
                e.printStackTrace()
                null
            }
        }

        // 1b. Shared Library Folder (via SAF)
        val libraryUriStr = vsh.M.pref.get(PrefEntry.USER_ICON_LIBRARY_URI, "")
        if(libraryUriStr.isNotEmpty()) {
            try {
                val treeUri = Uri.parse(libraryUriStr)
                val tree = DocumentFile.fromTreeUri(vsh, treeUri)
                if(tree != null && tree.exists()) {
                    val typeDir = tree.findFile(subDir)
                    if(typeDir != null && typeDir.isDirectory) {
                        val iconFile = typeDir.findFile("$id.png") ?: typeDir.findFile("$id.jpg")
                        if(iconFile != null && iconFile.exists()) {
                            return try {
                                vsh.contentResolver.openInputStream(iconFile.uri)?.use { stream ->
                                    BitmapFactory.decodeStream(stream)?.let(::createFixedSizeBitmap)
                                }
                            } catch(e: Exception) {
                                e.printStackTrace()
                                null
                            }
                        }
                    }
                }
            } catch(e: Exception) {
                Logger.e(TAG, "Failed to load icon from library folder: ${e.message}")
            }
        }

        return null
    }

    private fun loadThemeIconBitmap(type: IconType, id: String): Bitmap? {
        val activeTheme = vsh.M.customizer.activeTheme ?: return null
        val assetPath = when (type) {
            IconType.Category -> activeTheme.categoryIcons?.get(id)
            IconType.Node -> activeTheme.nodeIcons?.get(id)
            IconType.Settings -> null
        } ?: return null

        return try {
            ZipInputStream(File(activeTheme.id).inputStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == assetPath) {
                        return@use BitmapFactory.decodeStream(zip)?.let(::createFixedSizeBitmap)
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
                null
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to load theme icon $assetPath: ${e.message}")
            null
        }
    }

    fun setUserIcon(type: IconType, id: String, sourceFile: File?) {
        val subDir = subDirForType(type)
        val targetFile = File(vsh.getExternalFilesDir(null), "$CUSTOM_ICON_DIR/$subDir/$id.png")
        if(sourceFile == null || !sourceFile.exists()) {
            if(targetFile.exists()) targetFile.delete()
        } else {
            val parent = targetFile.parentFile
            if(parent != null && !parent.exists()) parent.mkdirs()
            sourceFile.copyTo(targetFile, overwrite = true)
        }

        // Evict from cache
        vsh.M.bmp.forceEvict("user://$subDir/$id")
        vsh.M.bmp.forceEvict("lib://$subDir/$id")
        vsh.M.bmp.forceEvict("bundled://${type.name.lowercase()}/$id")
    }

    fun resetAllUserIcons() {
        val dir = File(vsh.getExternalFilesDir(null), CUSTOM_ICON_DIR)
        if(dir.exists()) {
            dir.deleteRecursively()
        }
        vsh.M.bmp.releaseAll()
    }

    private fun loadIcon(id: String, type: IconType, fallbackRes: Int): Bitmap {
        val mapping = when(type) {
            IconType.Category -> categoryMappings[id]
            IconType.Node -> nodeMappings[id]
            IconType.Settings -> settingsMappings[id]
        }

        val ogMapping = when(type) {
            IconType.Category -> ogCategoryMappings[id]
            IconType.Node -> ogNodeMappings[id]
            IconType.Settings -> ogSettingsMapping(id)
        }

        if (ogMapping != null) {
            loadOgIconBitmap(ogMapping)?.let { return it }
        }

        val primaryFolder = when(type) {
            IconType.Category -> CATEGORY_PATH
            IconType.Node -> NODE_PATH
            IconType.Settings -> SETTINGS_PATH
        }

        val supplementalFolder = when(type) {
            IconType.Category -> null
            IconType.Node -> UTILITY_PATH
            IconType.Settings -> null
        }

        if (mapping == null) {
            Logger.w(TAG, "No mapping found for $type ID: $id")
        } else {
            val folders = mutableListOf(primaryFolder)
            if (supplementalFolder != null) folders.add(supplementalFolder)

            for (folder in folders) {
                val assetPath = "$folder/$mapping"
                try {
                    vsh.assets.open(assetPath).use { stream ->
                        val bmp = BitmapFactory.decodeStream(stream)
                        if (bmp != null) {
                            return createFixedSizeBitmap(bmp)
                        } else {
                            Logger.e(TAG, "Failed to decode asset: $assetPath")
                        }
                    }
                } catch (e: IOException) {
                    // Try next folder
                } catch (e: Exception) {
                    Logger.e(TAG, "Error loading asset $assetPath: ${e.message}")
                }
            }
            Logger.w(TAG, "Asset file \"$mapping\" not found for $id in $folders")
        }

        // Final fallback to drawable resource
        return try {
            val dr = ResourcesCompat.getDrawable(vsh.resources, fallbackRes, null)
            if (dr != null) {
                val bmp = dr.toBitmap(dr.intrinsicWidth, dr.intrinsicHeight)
                createFixedSizeBitmap(bmp)
            } else {
                XmbItem.TRANSPARENT_BITMAP
            }
        } catch(e:Exception) {
            XmbItem.TRANSPARENT_BITMAP
        }
    }

    private fun subDirForType(type: IconType): String {
        return when(type) {
            IconType.Category -> CUSTOM_CATEGORY_DIR
            IconType.Node -> CUSTOM_NODE_DIR
            IconType.Settings -> CUSTOM_SETTINGS_DIR
        }
    }

    private fun ogSettingsMapping(id: String): String? {
        return settingsMappings[id]?.replace(".32bit", "")
    }

    private fun loadOgIconBitmap(fileName: String): Bitmap? {
        return loadAssetBitmap("$OG_ASSET_BASE_PATH/$fileName")
    }

    private fun loadAssetBitmap(assetPath: String): Bitmap? {
        return try {
            vsh.assets.open(assetPath).use { stream ->
                BitmapFactory.decodeStream(stream)?.let(::createFixedSizeBitmap)
            }
        } catch (_: IOException) {
            null
        } catch (e: Exception) {
            Logger.e(TAG, "Error loading icon asset $assetPath: ${e.message}")
            null
        }
    }

    private fun createFixedSizeBitmap(src: Bitmap): Bitmap {
        val targetSize = Vsh.ITEM_BUILTIN_ICON_BITMAP_SIZE

        val w = src.width.toFloat()
        val h = src.height.toFloat()

        // If already perfect, return as is
        if (w == targetSize.toFloat() && h == targetSize.toFloat()) return src

        val scale = if (w > h) targetSize.toFloat() / w else targetSize.toFloat() / h

        val sw = (w * scale).toInt().coerceAtLeast(1)
        val sh = (h * scale).toInt().coerceAtLeast(1)

        val scaled = Bitmap.createScaledBitmap(src, sw, sh, true)
        val out = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val left = (targetSize - sw) / 2f
        val top = (targetSize - sh) / 2f
        canvas.drawBitmap(scaled, left, top, null)

        if (scaled != src) scaled.recycle()
        src.recycle()

        return out
    }

    private fun findOpaqueBounds(src: Bitmap): Rect? {
        var left = src.width
        var top = src.height
        var right = -1
        var bottom = -1

        for (y in 0 until src.height) {
            for (x in 0 until src.width) {
                if ((src.getPixel(x, y) ushr 24) != 0) {
                    if (x < left) left = x
                    if (y < top) top = y
                    if (x > right) right = x
                    if (y > bottom) bottom = y
                }
            }
        }

        if (right < left || bottom < top) return null
        return Rect(left, top, right + 1, bottom + 1)
    }

    private fun applyDefaultShellShadow(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = PorterDuffColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN)
        }
        val opaqueBounds = findOpaqueBounds(src)
        val shadowBitmap = if (opaqueBounds != null) {
            Bitmap.createBitmap(src, opaqueBounds.left, opaqueBounds.top, opaqueBounds.width(), opaqueBounds.height())
        } else {
            src
        }
        val shadowLeft = opaqueBounds?.left?.toFloat() ?: 0.0f
        val shadowTop = opaqueBounds?.top?.toFloat() ?: 0.0f
        val passes = arrayOf(
            floatArrayOf(4.0f, -1.0f, 2.0f),
            floatArrayOf(5.0f, 0.0f, 4.0f),
            floatArrayOf(6.0f, 1.0f, 6.0f),
            floatArrayOf(7.0f, 2.0f, 8.0f),
            floatArrayOf(8.0f, 3.0f, 10.0f),
            floatArrayOf(9.0f, 4.0f, 8.0f),
            floatArrayOf(10.0f, 5.0f, 6.0f),
            floatArrayOf(11.0f, 6.0f, 4.0f),
            floatArrayOf(12.0f, 7.0f, 2.0f)
        )

        for (pass in passes) {
            shadowPaint.alpha = pass[2].toInt()
            canvas.drawBitmap(shadowBitmap, shadowLeft + pass[0], shadowTop + pass[1], shadowPaint)
        }

        canvas.drawBitmap(src, 0.0f, 0.0f, null)
        if (shadowBitmap != src) shadowBitmap.recycle()
        src.recycle()
        return out
    }
}
