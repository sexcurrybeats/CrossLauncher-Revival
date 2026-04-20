package id.psw.vshlauncher.types.items

import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import id.psw.vshlauncher.Vsh
import id.psw.vshlauncher.submodules.BitmapRef
import id.psw.vshlauncher.types.XmbItem

class XmbSettingsItem(
    vsh: Vsh,
    override val id : String,
    private val r_title : Int,
    private val r_desc : Int,
    private val r_icon : Int,
    private val value_get : () -> String,
    private val on_launch : () -> Unit
) : XmbItem(vsh) {
    private var _titleStr : String? = null
    private var _descStr : String? = null

    constructor(
        vsh: Vsh,
        id: String,
        title: () -> String,
        desc: () -> String,
        r_icon: Int,
        value_get: () -> String,
        on_launch: () -> Unit
    ) : this(vsh, id, 0, 0, r_icon, value_get, on_launch) {
        _titleStr = title()
        _descStr = desc()
    }

    override val displayName: String get() = _titleStr ?: vsh.getString(r_title)
    override val description: String get() = _descStr ?: vsh.getString(r_desc)
    override val value: String get() = value_get()
    override val hasValue: Boolean = true
    override val hasDescription: Boolean get() = description.isNotBlank()
    override var hasMenu: Boolean = false
    override var menuItems: ArrayList<XmbMenuItem>? = null
    override val hasIcon: Boolean = true
    private val _iconRef = vsh.M.iconManager.getSettingsIcon(id, r_icon)
    override val isIconLoaded: Boolean get() = _iconRef.isLoaded
    override val icon get() = _iconRef.bitmap
    override val isHidden: Boolean get() = checkIsHidden()
    var checkIsHidden : () -> Boolean = {
        false
    }

    private fun callLaunch(x:XmbItem){
        on_launch()
    }

    override val onLaunch: (XmbItem) -> Unit
        get() = ::callLaunch
}