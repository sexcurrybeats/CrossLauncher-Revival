package id.psw.vshlauncher.types.items

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.scale
import id.psw.vshlauncher.*
import id.psw.vshlauncher.submodules.BitmapRef
import id.psw.vshlauncher.types.FileQuery
import id.psw.vshlauncher.types.XmbItem

class XmbItemCategory(
    vsh: Vsh, private val cateId:String,
    val strId : Int, private val iconId: Int,
    val sortable: Boolean = false, defaultSortIndex : Int,
    private val displayNameProvider: (() -> String)? = null
    ) : XmbItem(vsh) {
    private val _content = ArrayList<XmbItem>()
    private fun _postNoLaunchNotification(xmb: XmbItem){
        vsh.postNotification(null, vsh.getString(R.string.error_common_header), vsh.getString(R.string.error_category_launch))
    }

    private var _isLoadingIcon = false
    private var _icon : BitmapRef
    override val isIconLoaded: Boolean get() = _icon.isLoaded
    override val hasBackSound: Boolean = false
    override val hasBackdrop: Boolean = false
    override val hasContent: Boolean = true
    override val hasIcon: Boolean = true
    override val hasAnimatedIcon: Boolean = false
    override val hasDescription: Boolean = false
    override val hasMenu = false
    override val isHidden: Boolean
        get() = vsh.isCategoryHidden(id)

    override val displayName: String get() = displayNameProvider?.invoke() ?: vsh.getString(strId)
    override val icon: Bitmap get() = _icon.bitmap
    override val id: String get() = cateId
    private var _sortIndex = 0

    private val pkSortIndex : String get() ="/crosslauncher/${Consts.CATEGORY_SORT_INDEX_PREFIX}/${cateId}"

    var sortIndex : Int
        get() = _sortIndex
        set(value) {
            _sortIndex = value
            vsh.M.pref.set(pkSortIndex, _sortIndex)
        }

    private fun makeCustomResName(name:String) : String{
        var r = name
        if(r.startsWith("vsh_") && r.length >= 4) r = "explore_category_${r.substring(4)}"
        return r
    }

    init {
        _sortIndex = vsh.M.pref.get(pkSortIndex, defaultSortIndex)
        _icon = vsh.M.iconManager.getCategoryIcon(cateId, iconId)
    }

    override val content: ArrayList<XmbItem> get() = _content

    fun findNode(id: String): XmbItem? = _content.find { it.id == id }

    fun addItem(item: XmbItem) {
        if(_content.indexOfFirst { it.id == item.id } == -1){
            _content.add(item)
        }
    }

    var onSetSortFunc : (XmbItemCategory, Any) -> Unit = { _, _sortMode -> }
    var onSwitchSortFunc : (XmbItemCategory) -> Unit = { }
    var getSortModeNameFunc : (XmbItemCategory) -> String = { "" }

    fun onSwitchSort() = onSwitchSortFunc(this)
    fun <T> setSort(sort:T) {
        synchronized(this){
            onSetSortFunc(this, sort as Any)
        }
    }
    val sortModeName : String get() = getSortModeNameFunc(this)

    override val onLaunch: (XmbItem) -> Unit get() = ::_postNoLaunchNotification
}
