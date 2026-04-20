package id.psw.vshlauncher.types.items

import android.graphics.Bitmap
import id.psw.vshlauncher.Vsh
import id.psw.vshlauncher.submodules.BitmapRef
import id.psw.vshlauncher.types.XmbItem

class XmbNodeItem(
    vsh: Vsh,
    private val nodeId: String,
    private val nameId: Int,
    private val iconId: Int,
    private val descriptionId: Int = 0,
    val builtInDefault: Boolean = false,
    val userHideable: Boolean = false,
    val userDeletable: Boolean = false,
    val builtInCategoryId: String = "",
    private val launchAction: ((XmbNodeItem) -> Unit)? = null,
    private val itemMenuItems: ArrayList<XmbMenuItem>? = null
) : XmbItem(vsh) {
    private val _content = ArrayList<XmbItem>()
    private val _iconRef: BitmapRef = vsh.M.iconManager.getNodeIcon(nodeId, iconId)

    private var _nameStr: String? = null
    private var _descStr: String? = null
    private var _valueStr: String? = null
    private var _valueGetter: (() -> String)? = null

    constructor(
        vsh: Vsh,
        nodeId: String,
        name: String,
        iconId: Int,
        description: String = "",
        builtInDefault: Boolean = false,
        userHideable: Boolean = false,
        userDeletable: Boolean = false,
        builtInCategoryId: String = "",
        launchAction: ((XmbNodeItem) -> Unit)? = null,
        itemMenuItems: ArrayList<XmbMenuItem>? = null
    ) : this(vsh, nodeId, 0, iconId, 0, builtInDefault, userHideable, userDeletable, builtInCategoryId, launchAction, itemMenuItems) {
        _nameStr = name
        _descStr = description
    }

    override val id: String get() = nodeId
    override val displayName: String get() = _nameStr ?: vsh.getString(nameId)
    override val description: String get() = _descStr ?: (if (descriptionId != 0) vsh.getString(descriptionId) else "")
    override val hasDescription: Boolean get() = _descStr?.isNotEmpty() ?: (descriptionId != 0)
    override val value: String get() = _valueGetter?.invoke() ?: (_valueStr ?: "")
    override val hasValue: Boolean get() = value.isNotBlank()
    override val icon: Bitmap get() = _iconRef.bitmap
    override val isIconLoaded: Boolean get() = _iconRef.isLoaded
    override val hasIcon: Boolean get() = true
    override val content: ArrayList<XmbItem> get() = _content
    override val hasContent: Boolean get() = launchAction == null
    override val menuItems: ArrayList<XmbMenuItem>? get() = itemMenuItems
    override val isHidden: Boolean get() = userHideable && vsh.isBuiltInNodeEffectivelyHidden(nodeId)
    override val onLaunch: (XmbItem) -> Unit get() = ::callLaunch

    private fun callLaunch(item: XmbItem) {
        launchAction?.invoke(this)
    }

    fun setValue(value: String): XmbNodeItem {
        _valueStr = value
        _valueGetter = null
        return this
    }

    fun setValueGetter(getter: () -> String): XmbNodeItem {
        _valueGetter = getter
        _valueStr = null
        return this
    }

    fun addItem(item: XmbItem) {
        synchronized(_content) {
            if (_content.none { it.id == item.id }) {
                _content.add(item)
            }
        }
    }
}
