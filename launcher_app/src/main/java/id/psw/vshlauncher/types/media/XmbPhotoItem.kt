package id.psw.vshlauncher.types.media

import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.util.Size
import id.psw.vshlauncher.Vsh
import id.psw.vshlauncher.addToCategory
import id.psw.vshlauncher.sdkAtLeast
import id.psw.vshlauncher.submodules.BitmapRef
import id.psw.vshlauncher.types.XmbItem
import id.psw.vshlauncher.types.items.XmbMenuItem
import java.io.File

class XmbPhotoItem(vsh:Vsh, val data: PhotoData) : XmbItem(vsh) {
    override val id: String = "XMB_PHOTO_${data.id}"
    override val displayName: String = data.name
    override val description: String = "${data.date} - ${data.size / 1024} KB"
    override val hasDescription: Boolean = true

    private val defaultBitmap = BitmapRef("none", { TRANSPARENT_BITMAP }, BitmapRef.FallbackColor.Transparent)
    private var _icon : BitmapRef = defaultBitmap
    private val _itemMenus = arrayListOf<XmbMenuItem>()

    override val isIconLoaded: Boolean get() = _icon.isLoaded
    override val hasIcon: Boolean get() = true
    override val icon: Bitmap get() = _icon.bitmap

    override val hasMenu: Boolean get() = true
    override val menuItems: ArrayList<XmbMenuItem>? get() = _itemMenus
    override val menuItemCount: Int get() = _itemMenus.size

    private fun iconLoader() : Bitmap? {
        return if (sdkAtLeast(29)) {
            try {
                vsh.contentResolver.loadThumbnail(data.uri, Size(300, 300), null)
            } catch (e: Exception) {
                null
            }
        } else {
            MediaStore.Images.Thumbnails.getThumbnail(
                vsh.contentResolver,
                data.id,
                MediaStore.Images.Thumbnails.MINI_KIND,
                null
            )
        }
    }

    private fun loadIcon(i: XmbItem) {
        _icon = BitmapRef("photo_thumb_$id", ::iconLoader)
    }

    private fun unloadIcon(i: XmbItem) {
        if (_icon != defaultBitmap) _icon.release()
        _icon = defaultBitmap
    }

    private fun launch(i: XmbItem) {
        vsh.openFileOnExternalApp(File(data.data))
    }

    init {
        vsh.M.media.createMediaMenuItems(_itemMenus, data)
    }

    override val onLaunch: (XmbItem) -> Unit = ::launch
    override val onScreenVisible: (XmbItem) -> Unit = ::loadIcon
    override val onScreenInvisible: (XmbItem) -> Unit = ::unloadIcon
}

data class PhotoData(
    override val id: Long,
    override val uri: Uri,
    override val data: String,
    val name: String,
    val size: Long,
    val date: String,
    val mime: String
) : MediaData(id, uri, data)
