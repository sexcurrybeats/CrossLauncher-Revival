package id.psw.vshlauncher.types.items

import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import id.psw.vshlauncher.Logger
import id.psw.vshlauncher.R
import id.psw.vshlauncher.Vsh
import id.psw.vshlauncher.VshBaseDirs
import id.psw.vshlauncher.VshResTypes
import id.psw.vshlauncher.postNotification
import id.psw.vshlauncher.requireValidAnimatedIconImport
import id.psw.vshlauncher.submodules.BitmapRef
import id.psw.vshlauncher.types.CifLoader
import id.psw.vshlauncher.types.FileQuery
import id.psw.vshlauncher.types.XmbItem
import id.psw.vshlauncher.types.sequentialimages.XmbFrameAnimation
import java.io.File

class XmbCustomLaunchItem(
    vsh: Vsh,
    private val record: Vsh.CustomLaunchItemRecord,
    fallbackIconId: Int,
    private val itemMenuItems: ArrayList<XmbMenuItem>,
    private val launchAction: (Vsh.CustomLaunchItemRecord) -> Unit
) : XmbItem(vsh) {
    companion object {
        private const val TAG = "CustomLaunchItem"
        private const val CUSTOM_LAUNCH_DIR = "custom_launch"
    }

    private val customDir: File = FileQuery(VshBaseDirs.APPS_DIR)
        .atPath(CUSTOM_LAUNCH_DIR, record.itemId)
        .execute(vsh)
        .firstOrNull()
        ?: File(vsh.filesDir, "${VshBaseDirs.APPS_DIR}/$CUSTOM_LAUNCH_DIR/${record.itemId}")

    private val cif = CifLoader(vsh, record.itemId, customDir)
    private val fallbackIconRef: BitmapRef = vsh.M.iconManager.getNodeIcon(record.itemId, fallbackIconId)

    override val id: String get() = record.itemId
    override val displayName: String get() = record.title
    override val description: String get() = record.platformId.uppercase()
    override val hasDescription: Boolean get() = record.platformId.isNotBlank() && record.platformId != "unknown"
    override val value: String get() = record.packageName.ifBlank { record.fileUri.ifBlank { record.target } }
    override val hasValue: Boolean get() = value.isNotBlank()
    override val hasIcon: Boolean get() = true
    override val isIconLoaded: Boolean get() = if (hasCustomIcon()) cif.icon.isLoaded else fallbackIconRef.isLoaded
    override val icon: Bitmap get() = if (hasCustomIcon()) cif.icon.bitmap else fallbackIconRef.bitmap
    override val hasAnimatedIcon: Boolean get() = hasCustomAnimatedIcon() && cif.hasAnimatedIcon
    override val isAnimatedIconLoaded: Boolean get() = cif.hasAnimIconLoaded
    override val animatedIcon: XmbFrameAnimation get() = synchronized(cif.animIcon) { cif.animIcon }
    override val hasBackdrop: Boolean get() = hasAsset("PIC1", VshResTypes.IMAGES)
    override val isBackdropLoaded: Boolean get() = cif.hasBackdropLoaded
    override val backdrop: Bitmap get() = cif.backdrop.bitmap
    override val hasBackOverlay: Boolean get() = hasAsset("PIC0", VshResTypes.IMAGES)
    override val isBackdropOverlayLoaded: Boolean get() = cif.hasBackdropOverlayLoaded
    override val backdropOverlay: Bitmap get() = cif.backOverlay.bitmap
    override val hasPortraitBackdrop: Boolean get() = hasAsset("PIC1_P", VshResTypes.IMAGES)
    override val isPortraitBackdropLoaded: Boolean get() = cif.hasPortBackdropLoaded
    override val portraitBackdrop: Bitmap get() = cif.portBackdrop.bitmap
    override val hasPortraitBackdropOverlay: Boolean get() = hasAsset("PIC0_P", VshResTypes.IMAGES)
    override val isPortraitBackdropOverlayLoaded: Boolean get() = cif.hasPortBackdropOverlayLoaded
    override val portraitBackdropOverlay: Bitmap get() = cif.portBackOverlay.bitmap
    override val menuItems: ArrayList<XmbMenuItem> get() = itemMenuItems
    override val onLaunch: (XmbItem) -> Unit get() = { launchAction(record) }
    override val onScreenVisible: (XmbItem) -> Unit get() = {
        vsh.threadPool.execute {
            if (hasCustomIcon() || hasCustomAnimatedIcon()) {
                cif.loadIcon()
            }
        }
    }
    override val onHovered: (XmbItem) -> Unit get() = {
        vsh.threadPool.execute {
            if (hasBackdrop || hasBackOverlay || hasPortraitBackdrop || hasPortraitBackdropOverlay) {
                cif.loadBackdrop()
            }
        }
    }
    override val onUnHovered: (XmbItem) -> Unit get() = {
        vsh.threadPool.execute {
            cif.unloadBackdrop()
        }
    }

    private fun hasCustomIcon(): Boolean = hasAsset("ICON0", VshResTypes.ICONS)

    private fun hasCustomAnimatedIcon(): Boolean = hasAsset("ICON1", VshResTypes.ANIMATED_ICONS)

    val launchType: String get() = record.type
    val platformId: String get() = record.platformId
    val emulatorPackageName: String get() = record.packageName
    val targetSummary: String get() = value
    val gamebootEnabled: Boolean get() = record.gamebootEnabled
    val customIconPath: String get() = findAssetFile("ICON0", VshResTypes.ICONS)?.absolutePath ?: ""
    val customAnimIconPath: String get() = findAssetFile("ICON1", VshResTypes.ANIMATED_ICONS)?.absolutePath ?: ""
    val customBackdropPath: String get() = findAssetFile("PIC1", VshResTypes.IMAGES)?.absolutePath ?: ""
    val customBackdropOverlayPath: String get() = findAssetFile("PIC0", VshResTypes.IMAGES)?.absolutePath ?: ""

    private fun hasAsset(baseName: String, extensions: Array<String>): Boolean {
        return extensions.any { File(customDir, "$baseName.$it").exists() }
    }

    private fun findAssetFile(baseName: String, extensions: Array<String>): File? {
        return extensions.firstNotNullOfOrNull { ext ->
            File(customDir, "$baseName.$ext").takeIf { it.exists() }
        }
    }

    private fun deleteAssetFiles(vararg baseNames: String) {
        baseNames.forEach { baseName ->
            (VshResTypes.ICONS + VshResTypes.IMAGES + VshResTypes.ANIMATED_ICONS + VshResTypes.SOUNDS).forEach { ext ->
                File(customDir, "$baseName.$ext").delete()
            }
        }
    }

    fun importCustomAsset(uri: Uri, assetType: Int) {
        vsh.threadPool.execute {
            try {
                if (!customDir.exists()) customDir.mkdirs()
                val fileExt = resolveFileExtension(uri, assetType)
                val baseName = when (assetType) {
                    Vsh.ACT_REQ_PICK_CUSTOM_ICON -> "ICON0"
                    Vsh.ACT_REQ_PICK_CUSTOM_BACKDROP -> "PIC1"
                    Vsh.ACT_REQ_PICK_CUSTOM_BACKDROP_OVERLAY -> "PIC0"
                    Vsh.ACT_REQ_PICK_CUSTOM_BACK_SOUND -> "SND0"
                    Vsh.ACT_REQ_PICK_CUSTOM_ANIM_ICON -> "ICON1"
                    Vsh.ACT_REQ_PICK_CUSTOM_PORT_BACKDROP -> "PIC1_P"
                    Vsh.ACT_REQ_PICK_CUSTOM_PORT_BACKDROP_OVERLAY -> "PIC0_P"
                    else -> "CUSTOM"
                }
                deleteAssetFiles(baseName)
                val destFile = File(customDir, "$baseName.$fileExt")
                vsh.contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { output -> input.copyTo(output) }
                }

                when (assetType) {
                    Vsh.ACT_REQ_PICK_CUSTOM_ICON -> {
                        cif.customIconFile = destFile
                        vsh.mainHandle.post {
                            cif.unloadIcon(true)
                            cif.loadIcon()
                        }
                    }
                    Vsh.ACT_REQ_PICK_CUSTOM_ANIM_ICON -> {
                        cif.customAnimIconFile = destFile
                        vsh.mainHandle.post {
                            cif.unloadIcon(true)
                            cif.loadIcon()
                        }
                    }
                    Vsh.ACT_REQ_PICK_CUSTOM_BACKDROP -> {
                        cif.customBackdropFile = destFile
                        vsh.mainHandle.post {
                            cif.unloadBackdrop(true)
                            cif.loadBackdrop()
                        }
                    }
                    Vsh.ACT_REQ_PICK_CUSTOM_BACKDROP_OVERLAY -> {
                        cif.customBackdropOverlayFile = destFile
                        vsh.mainHandle.post {
                            cif.unloadBackdrop(true)
                            cif.loadBackdrop()
                        }
                    }
                    Vsh.ACT_REQ_PICK_CUSTOM_PORT_BACKDROP -> {
                        cif.customPortraitBackdropFile = destFile
                        vsh.mainHandle.post {
                            cif.unloadBackdrop(true)
                            cif.loadBackdrop()
                        }
                    }
                    Vsh.ACT_REQ_PICK_CUSTOM_PORT_BACKDROP_OVERLAY -> {
                        cif.customPortraitBackdropOverlayFile = destFile
                        vsh.mainHandle.post {
                            cif.unloadBackdrop(true)
                            cif.loadBackdrop()
                        }
                    }
                }

                vsh.postNotification(R.drawable.category_setting, displayName, vsh.getString(R.string.common_success))
            } catch (e: Exception) {
                Logger.exc(TAG, Thread.currentThread(), e)
                vsh.postNotification(R.drawable.ic_error, "Import Failed", e.localizedMessage ?: "Unknown error")
            }
        }
    }

    private fun resolveFileExtension(uri: Uri, assetType: Int): String {
        var fileExt = ""
        vsh.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                fileExt = cursor.getString(nameIndex).substringAfterLast('.', "")
            }
        }

        if (fileExt.isBlank()) {
            fileExt = when (vsh.contentResolver.getType(uri)) {
                "image/gif" -> "gif"
                "image/webp" -> "webp"
                "image/png" -> "png"
                "image/jpeg" -> "jpg"
                "audio/mpeg" -> "mp3"
                "audio/ogg" -> "ogg"
                else -> if (assetType == Vsh.ACT_REQ_PICK_CUSTOM_BACK_SOUND) "audio" else "img"
            }
        }

        if (assetType == Vsh.ACT_REQ_PICK_CUSTOM_ANIM_ICON) {
            val assetSize = vsh.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
            vsh.requireValidAnimatedIconImport(fileExt, assetSize)
        }
        return fileExt
    }

    fun resetCustomIcon() {
        deleteAssetFiles("ICON0", "ICON1")
        cif.customIconFile = null
        cif.customAnimIconFile = null
        vsh.mainHandle.post {
            cif.unloadIcon(true)
            cif.loadIcon()
        }
    }

    fun resetCustomBackdrop() {
        deleteAssetFiles("PIC1", "PIC0", "PIC1_P", "PIC0_P")
        cif.customBackdropFile = null
        cif.customBackdropOverlayFile = null
        cif.customPortraitBackdropFile = null
        cif.customPortraitBackdropOverlayFile = null
        vsh.mainHandle.post {
            cif.unloadBackdrop(true)
            cif.loadBackdrop()
        }
    }
}
