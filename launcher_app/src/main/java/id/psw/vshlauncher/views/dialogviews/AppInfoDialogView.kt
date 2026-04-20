package id.psw.vshlauncher.views.dialogviews

import android.content.Intent
import android.graphics.*
import android.text.TextPaint
import android.view.MotionEvent
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.withRotation
import id.psw.vshlauncher.*
import id.psw.vshlauncher.submodules.PadKey
import id.psw.vshlauncher.types.XmbItem
import id.psw.vshlauncher.types.items.XmbAppItem
import id.psw.vshlauncher.types.items.XmbMenuItem
import id.psw.vshlauncher.typography.FontCollections
import id.psw.vshlauncher.views.XmbDialogSubview
import id.psw.vshlauncher.views.XmbView
import id.psw.vshlauncher.views.drawBitmap
import id.psw.vshlauncher.views.nativedlg.NativeEditTextDialog
import kotlin.math.abs

class AppInfoDialogView(v: XmbView, private val app : XmbAppItem) : XmbDialogSubview(v) {
    companion object {
        const val POS_NAME = 0
        const val POS_DESC = 1
        const val POS_ALBUM = 2
        const val POS_HIDDEN = 3
        const val POS_CATEGORY = 4
        const val POS_NODE = 5
        const val POS_CUSTOM_ICON = 6
        const val POS_CUSTOM_BACKDROP = 7
        const val POS_CUSTOM_BACKDROP_OVERLAY = 8
        const val POS_CUSTOM_BACK_SOUND = 9
        const val POS_CUSTOM_ANIM_ICON = 10
        const val POS_CUSTOM_PORT_BACKDROP = 11
        const val POS_CUSTOM_PORT_BACKDROP_OVERLAY = 12
        const val POS_RESET_ICON = 13
        const val POS_RESET_BACKDROP = 14
        const val POS_RESET_ALL = 15
        const val POS_OPEN_IN_SYSTEM = 16
        const val TRANSITE_TIME = 0.125f

        private val bmpRectF = RectF()
        private val selRectF = RectF()
        private val szBufRectF = RectF()
        private val validSelections = arrayOf(0, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, -1)
    }

    override val hasNegativeButton: Boolean = true
    override val hasPositiveButton: Boolean get() = true
    override val title: String
        get() = vsh.getString(R.string.view_app_info)

    private var cursorPos = 0
    private var transiteTime = 0.0f
    private lateinit var loadIcon : Bitmap
    private var tPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 20.0f
        typeface = FontCollections.masterFont
    }

    private var iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var rectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply{
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2.0f
    }

    private var _icon =vsh.loadTexture(R.drawable.icon_info, "dialog_icon_app_info", true)

    override val icon: Bitmap
        get() = _icon

    override fun onClose() {
        if(_icon != XmbItem.WHITE_BITMAP)  _icon.recycle()
        if(loadIcon != XmbItem.WHITE_BITMAP)  loadIcon.recycle()

        // Move the icon to other category
        if(originalCategory != app.appCategory || originalNode != app.appNodeOverride){
            if(vsh.swapCategory(app.id, originalCategory, app.appCategory)){
                vsh.M.apps.reloadAppList()
            }
        }
    }

    override val negativeButton: String
        get() = vsh.getString(R.string.common_back)

    override val positiveButton: String
        get() = when(cursorPos) {
            3 -> vsh.getString(R.string.common_toggle)
            4, 5 -> vsh.getString(R.string.common_change)
            6 -> vsh.getString(R.string.category_settings)
            else -> vsh.getString(R.string.common_edit)
        }

    private var originalCategory = app.appCategory
    private var originalNode = app.appNodeOverride

    override fun onStart() {
        loadIcon = ResourcesCompat.getDrawable(vsh.resources,R.drawable.ic_sync_loading,null)?.toBitmap(256,256) ?: XmbItem.WHITE_BITMAP
        super.onStart()
    }

    private fun drawLoading(ctx:Canvas){
        val time = vsh.xmbView?.time?.currentTime ?: (System.currentTimeMillis() / 1000.0f)
        ctx.withRotation(
            ((time + 0.375f) * -360.0f) % 360.0f, bmpRectF.centerX(), bmpRectF.centerY()) {
            ctx.drawBitmap(loadIcon, null, bmpRectF, iconPaint, FittingMode.FIT, 0.5f, 0.5f)
        }

    }

    private val drawBound = RectF()

    override fun onDraw(ctx: Canvas, drawBound: RectF, deltaTime: Float) {
        this.drawBound.set(drawBound)

        if(transiteTime < TRANSITE_TIME){
            transiteTime += vsh.xmbView?.time?.deltaTime ?: 0.015f
        }
        transiteTime = transiteTime.coerceIn(0.0f, TRANSITE_TIME)

        val t = transiteTime / TRANSITE_TIME

        val cSizeY = 132.0f
        val sizeX = t.toLerp(320.0f, 240.0f)
        val sizeY = t.toLerp(176.0f, cSizeY)

        val hSizeX = sizeX / 2.0f
        val hSizeY = sizeY / 2.0f

        val iconCX = t.toLerp(
            (0.3f).toLerp(drawBound.left, drawBound.right),
            drawBound.centerX())
        val iconCY = t.toLerp(
            drawBound.centerY(),
            drawBound.top + 20.0f + hSizeY
        )

        bmpRectF.set(
            iconCX - hSizeX,
            iconCY - hSizeY,
            iconCX + hSizeX,
            iconCY + hSizeY
        )

        if(app.hasAnimatedIcon){
            if(app.isAnimatedIconLoaded){
                val tt = vsh.xmbView?.time?.deltaTime ?: 0.015f
                ctx.drawBitmap(app.animatedIcon.getFrame(tt), null, bmpRectF, iconPaint, FittingMode.FIT, 0.5f, 0.5f)
            }else{
                drawLoading(ctx)
            }
        }else if(app.hasIcon){
            if(app.isIconLoaded){
                ctx.drawBitmap(app.icon, null, bmpRectF, iconPaint, FittingMode.FIT, 0.5f, 0.5f)
            }else{
                drawLoading(ctx)
            }
        }

        var sY = drawBound.top + cSizeY + 50.0f
        val cX = drawBound.centerX() - 100.0f

        val routing = vsh.resolveRouting(app)
        val routingText = "${getCategoryDisplay(routing.categoryId)} > ${getNodeDisplay(routing.categoryId, routing.nodeId ?: "")}"

        val items = mutableListOf<Pair<Int, String>>(
            R.string.dlg_info_name to app.displayName,
            R.string.dlg_info_pkg_name to app.packageName,
            R.string.dlg_info_desc to app.appCustomDesc.ifEmpty { "-" },
            R.string.dlg_info_album to app.appAlbum.ifEmpty { "-" },
            R.string.dlg_info_hidden to vsh.getString(app.isHiddenByCfg.select(R.string.common_yes, R.string.common_no)),
            R.string.dlg_info_category to getCategoryDisplay(app.appCategory),
            R.string.dlg_info_node to getNodeDisplay(app.appCategory, app.appNodeOverride),
            R.string.dlg_info_custom_icon to app.customIconPath.ifEmpty { "-" },
            R.string.dlg_info_custom_backdrop to app.customBackdropPath.ifEmpty { "-" },
            R.string.dlg_info_custom_backdrop_overlay to app.customBackdropOverlayPath.ifEmpty { "-" },
            R.string.dlg_info_custom_back_sound to app.customBackSoundPath.ifEmpty { "-" },
            R.string.dlg_info_custom_anim_icon to app.customAnimIconPath.ifEmpty { "-" },
            R.string.dlg_info_custom_port_backdrop to app.customPortBackdropPath.ifEmpty { "-" },
            R.string.dlg_info_custom_port_backdrop_overlay to app.customPortBackdropOverlayPath.ifEmpty { "-" },
            R.string.dlg_info_reset_custom_icon to "",
            R.string.dlg_info_reset_custom_backdrop to "",
            R.string.dlg_info_reset_all_assets to vsh.getString(R.string.dlg_info_reset_desc),
            R.string.dlg_info_routing to routingText,
            R.string.dlg_info_update to app.displayUpdateTime,
            R.string.dlg_info_apk_size to app.fileSize,
            R.string.dlg_info_version to app.version
        )
        
        var i = 0
        items.forEach { (key, value) ->
            tPaint.textAlign = Paint.Align.RIGHT
            ctx.drawText(vsh.getString(key), cX, sY, tPaint)
            val str = value
            
            val selectionIndex = validSelections[cursorPos.coerceIn(0, validSelections.size - 1)]
            val isSelected = selectionIndex == i

            if(isSelected){
                val w = tPaint.measureText(str)
                selRectF.set(cX + 20.0f, sY - tPaint.textSize , cX + 50.0f + w, sY+ 5.0f)
                ctx.drawRoundRect(selRectF, 5.0f, 5.0f, rectPaint)
            }

            tPaint.textAlign = Paint.Align.LEFT
            ctx.drawText(str, cX + 30.0f, sY, tPaint)
            sY += tPaint.textSize * 1.2f
            i++
        }

        tPaint.textAlign = Paint.Align.CENTER
        if(cursorPos == POS_OPEN_IN_SYSTEM){ // Open in System
            val ccx = drawBound.centerX()
            selRectF.set(ccx - 300.0f, sY - tPaint.textSize + 20.0f, ccx + 300.0f, sY+ 5.0f + 20.0f)
            ctx.drawRoundRect(selRectF, 5.0f, 5.0f, rectPaint)
        }
        ctx.drawText(vsh.getString(R.string.app_info_system_activity), drawBound.centerX(), sY + 20.0f, tPaint)
    }

    private fun getCategoryDisplay(appCategory: String): String {
        return vsh.categories.find { it.id == appCategory }?.displayName ?: appCategory.ifEmpty { vsh.getString(R.string.common_default) }
    }

    private fun getNodeDisplay(appCategory: String, appNode: String): String {
        if (appNode.isEmpty()) return vsh.getString(R.string.common_default)
        val catId = appCategory.ifEmpty { 
             vsh.M.apps.isAGame(app.resInfo).select(Vsh.ITEM_CATEGORY_GAME, Vsh.ITEM_CATEGORY_APPS)
        }
        val cat = vsh.categories.find { it.id == catId }
        val node = cat?.findNode(appNode)
        return node?.displayName ?: appNode
    }

    override fun onGamepad(key: PadKey, isPress: Boolean): Boolean {
        when(key){
            PadKey.PadU -> {
                if(isPress){
                    cursorPos--
                    cursorPos = cursorPos.coerceIn(0, validSelections.size-1)
                    return true
                }
            }
            PadKey.PadD -> {
                if(isPress){
                    cursorPos ++
                    cursorPos = cursorPos.coerceIn(0, validSelections.size-1)
                    return true
                }
            }
            else -> {

            }
        }

        return super.onGamepad(key, isPress)
    }

    private var touchHasMove = false

    override fun onTouch(a: PointF, b: PointF, act: Int) {
        if(act == MotionEvent.ACTION_MOVE){
            val diff = a.y - b.y
            if(abs(diff) > 50.0f){
                if(diff > 0.0f){
                    cursorPos--
                }else{
                    cursorPos++
                }

                b.y += 100.0f
                touchHasMove = true
                vsh.xmbView!!.context.xmb.touchStartPointF.set(b)
                cursorPos = cursorPos.coerceIn(0, validSelections.size - 1)
            }
        }else if(act == MotionEvent.ACTION_UP){
            if(!touchHasMove){
                if(a.y > drawBound.height() * 0.6f){
                    cursorPos++
                }else if(a.y < drawBound.height() * 0.3f){
                    cursorPos--
                }
                cursorPos = cursorPos.coerceIn(0, validSelections.size - 1)
            }
            touchHasMove = false
        }else if(act == MotionEvent.ACTION_DOWN){
            touchHasMove = false
        }
        super.onTouch(a, b, act)
    }

    override fun onDialogButton(isPositive: Boolean) {
        if(isPositive){
            when(cursorPos){
                POS_NAME -> {
                    // Rename
                    NativeEditTextDialog(vsh)
                        .setTitle(vsh.getString(R.string.dlg_info_rename))
                        .setOnFinish {
                            app.appCustomLabel = it
                        }
                        .setValue(app.displayName)
                        .show()
                }
                POS_DESC -> {
                    NativeEditTextDialog(vsh)
                        .setTitle(vsh.getString(R.string.dlg_info_redesc))
                        .setOnFinish {
                            app.appCustomDesc = it
                        }
                        .setValue(app.appCustomDesc)
                        .show()
                }
                POS_ALBUM -> {
                    NativeEditTextDialog(vsh)
                        .setTitle(vsh.getString(R.string.dlg_info_album))
                        .setOnFinish {
                            app.appAlbum = it
                        }
                        .setValue(app.appAlbum)
                        .show()
                }
                POS_HIDDEN -> {
                    app.hide(!app.isHiddenByCfg)
                    // Change is Hidden
                }
                POS_CATEGORY -> {
                    // Set Category
                    val menus = arrayListOf<XmbMenuItem>()
                    var i  = 0
                    menus.add(XmbMenuItem.XmbMenuItemLambda({ vsh.getString(R.string.common_default) }, { false }, i++) {
                        app.appCategory = ""
                        app.appNodeOverride = ""
                    })

                    vsh.categories.forEach { cat ->
                        menus.add(XmbMenuItem.XmbMenuItemLambda({ cat.displayName }, { false }, i++) {
                            app.appCategory = cat.id
                            app.appNodeOverride = ""
                        })
                    }
                    view.widgets.sideMenu.show(menus)
                    view.widgets.sideMenu.selectedIndex = 0
                }
                POS_NODE -> {
                    // Set Node
                    val catId = app.appCategory.ifEmpty {
                        vsh.M.apps.isAGame(app.resInfo).select(Vsh.ITEM_CATEGORY_GAME, Vsh.ITEM_CATEGORY_APPS)
                    }
                    val cat = vsh.categories.find { it.id == catId }
                    val nodes = arrayListOf<XmbMenuItem>()
                    nodes.add(XmbMenuItem.XmbMenuItemLambda({ vsh.getString(R.string.common_default) }, { false }, 0) {
                        app.appNodeOverride = ""
                    })

                    var i = 1
                    cat?.content?.filterIsInstance<id.psw.vshlauncher.types.items.XmbNodeItem>()?.forEach { node ->
                        nodes.add(XmbMenuItem.XmbMenuItemLambda({ node.displayName }, { false }, i++) {
                            app.appNodeOverride = node.id
                        })
                    }

                    view.widgets.sideMenu.show(nodes)
                    view.widgets.sideMenu.selectedIndex = 0
                }
                POS_CUSTOM_ICON -> {
                    vsh.itemEditing = app
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "image/*"
                    }
                    vsh.xmbView?.context?.xmb?.startActivityForResult(intent, Vsh.ACT_REQ_PICK_CUSTOM_ICON)
                }
                POS_CUSTOM_BACKDROP -> {
                    vsh.itemEditing = app
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "image/*"
                    }
                    vsh.xmbView?.context?.xmb?.startActivityForResult(intent, Vsh.ACT_REQ_PICK_CUSTOM_BACKDROP)
                }
                POS_CUSTOM_BACKDROP_OVERLAY -> {
                    vsh.itemEditing = app
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "image/*"
                    }
                    vsh.xmbView?.context?.xmb?.startActivityForResult(intent, Vsh.ACT_REQ_PICK_CUSTOM_BACKDROP_OVERLAY)
                }
                POS_CUSTOM_BACK_SOUND -> {
                    vsh.itemEditing = app
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "audio/*"
                    }
                    vsh.xmbView?.context?.xmb?.startActivityForResult(intent, Vsh.ACT_REQ_PICK_CUSTOM_BACK_SOUND)
                }
                POS_CUSTOM_ANIM_ICON -> {
                    vsh.itemEditing = app
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                        // Add some common animated icon mimetypes
                        putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/webp", "image/apng", "image/gif"))
                    }
                    vsh.xmbView?.context?.xmb?.startActivityForResult(intent, Vsh.ACT_REQ_PICK_CUSTOM_ANIM_ICON)
                }
                POS_CUSTOM_PORT_BACKDROP -> {
                    vsh.itemEditing = app
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "image/*"
                    }
                    vsh.xmbView?.context?.xmb?.startActivityForResult(intent, Vsh.ACT_REQ_PICK_CUSTOM_PORT_BACKDROP)
                }
                POS_CUSTOM_PORT_BACKDROP_OVERLAY -> {
                    vsh.itemEditing = app
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "image/*"
                    }
                    vsh.xmbView?.context?.xmb?.startActivityForResult(intent, Vsh.ACT_REQ_PICK_CUSTOM_PORT_BACKDROP_OVERLAY)
                }
                POS_RESET_ICON -> {
                    app.resetCustomIcon()
                    vsh.postNotification(R.drawable.category_setting,
                        app.displayName,
                        vsh.getString(R.string.common_success))
                }
                POS_RESET_BACKDROP -> {
                    app.resetCustomBackdrop()
                    vsh.postNotification(R.drawable.category_setting,
                        app.displayName,
                        vsh.getString(R.string.common_success))
                }
                POS_RESET_ALL -> {
                    // Reset to Default
                    app.resetCustomAssets()

                    vsh.postNotification(R.drawable.category_setting,
                        app.displayName,
                        vsh.getString(R.string.common_success))
                }
                POS_OPEN_IN_SYSTEM -> {
                    // Show in Android
                    vsh.showAppInfo(app)
                }
            }
        }

        if(!isPositive) finish(view.screens.mainMenu)
    }
}
