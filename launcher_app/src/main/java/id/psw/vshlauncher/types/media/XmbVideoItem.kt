package id.psw.vshlauncher.types.media

import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import id.psw.vshlauncher.Vsh
import id.psw.vshlauncher.sdkAtLeast
import id.psw.vshlauncher.submodules.BitmapRef
import id.psw.vshlauncher.types.XmbItem
import id.psw.vshlauncher.types.items.XmbMenuItem
import id.psw.vshlauncher.views.asBytes
import java.io.File

class XmbVideoItem(vsh: Vsh, val data : VideoData) : XmbItem(vsh) {
    override val displayName: String
        get() = data.displayName

    override val id: String = "XMB_VIDEO_${data.id}"

    override val hasDescription: Boolean = true

    override val description: String
        get() = data.size.asBytes()

    private val defaultBitmap = BitmapRef("none", { TRANSPARENT_BITMAP }, BitmapRef.FallbackColor.Transparent)
    private var _iconRef : BitmapRef = defaultBitmap
    private val _itemMenus = arrayListOf<XmbMenuItem>()

    override val isIconLoaded: Boolean get() = _iconRef.isLoaded
    override val hasIcon: Boolean get() = true
    override val icon: Bitmap get() = _iconRef.bitmap

    override val hasMenu: Boolean get() = true
    override val menuItems: ArrayList<XmbMenuItem>? get() = _itemMenus
    override val menuItemCount: Int get() = _itemMenus.size

    private fun iconLoader() : Bitmap? {
        return try {
            if (sdkAtLeast(Build.VERSION_CODES.Q)) {
                vsh.contentResolver.loadThumbnail(data.uri, Size(320, 176), null)
            } else {
                MediaStore.Images.Thumbnails.getThumbnail(vsh.contentResolver, data.id, MediaStore.Images.Thumbnails.MINI_KIND, null)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun loadIcon(i:XmbItem){
        _iconRef = BitmapRef("video_thumb_$id", ::iconLoader)
    }

    private fun unloadIcon(i:XmbItem){
        if (_iconRef != defaultBitmap) _iconRef.release()
        _iconRef = defaultBitmap
    }

    init {
        vsh.M.media.createMediaMenuItems(_itemMenus, data)
    }

    private fun launch(i:XmbItem){
        vsh.openFileOnExternalApp(File(data.data))
    }

    override val onLaunch: (XmbItem) -> Unit
        get() = ::launch

    override val onScreenVisible: (XmbItem) -> Unit
        get() = ::loadIcon

    override val onScreenInvisible: (XmbItem) -> Unit
        get() = ::unloadIcon
}