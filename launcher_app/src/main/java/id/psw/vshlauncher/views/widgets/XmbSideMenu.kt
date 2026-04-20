package id.psw.vshlauncher.views.widgets

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.PointF
import android.graphics.RectF
import android.view.MotionEvent
import androidx.core.graphics.minus
import androidx.core.graphics.withRotation
import androidx.core.graphics.withTranslation
import id.psw.vshlauncher.FColor
import id.psw.vshlauncher.PrefEntry
import id.psw.vshlauncher.makeTextPaint
import id.psw.vshlauncher.select
import id.psw.vshlauncher.submodules.PadKey
import id.psw.vshlauncher.submodules.SfxType
import id.psw.vshlauncher.toLerp
import id.psw.vshlauncher.types.items.XmbMenuItem
import id.psw.vshlauncher.views.XmbLayoutType
import id.psw.vshlauncher.views.XmbView
import id.psw.vshlauncher.views.XmbWidget
import id.psw.vshlauncher.views.drawText
import kotlin.math.absoluteValue
import kotlin.math.sin

class XmbSideMenu(view: XmbView) : XmbWidget(view) {
    private val textPaint : Paint = vsh.makeTextPaint(10.0f)
    private val panelFill : Paint = vsh.makeTextPaint(style= Paint.Style.FILL, color = Color.argb(206, 178, 183, 192))
    private val selectedRowFill : Paint = vsh.makeTextPaint(style= Paint.Style.FILL, color = Color.argb(208, 248, 250, 252))

    enum class TouchInteractMode
    {
        Tap,
        Gesture;

        fun toInt() = ordinal
        companion object
        {
            fun fromInt(value: Int) = entries[value]
        }
    }

    enum class PanelWidthMode
    {
        Auto,
        Wide
    }

    var showMenuDisplayFactor = 0.0f
    var isDisplayed = false
        set(value) {
            if (field == value) return
            field = value
            if (!value) {
                items.clear()
                invalidateOrderedItems()
                vsh.hoveredItem?.setMenuOpened(false)
            }
        }
    var selectedIndex = 0
    var viewedIndex = Point(-5, 5)
    var viewRangeMinPlus = 5

    val itemMenuRectF = RectF()
    val items = arrayListOf<XmbMenuItem>()
    var disableMenuExec = false
    private var orderedItemsSource: ArrayList<XmbMenuItem>? = null
    private var orderedItemsSize = -1
    private var orderedItems: List<XmbMenuItem>? = null
    private var panelWidthMode = PanelWidthMode.Auto

    var interactionMode : TouchInteractMode
        get()
        {
            val defVal = TouchInteractMode.Tap.toInt()
            val saved = vsh.M.pref.get(PrefEntry.SIDEMENU_TOUCH_INTERACTION_MODE, defVal)
            return TouchInteractMode.fromInt(saved)
        }
        set(value)
        {
            vsh.M.pref.set(PrefEntry.SIDEMENU_TOUCH_INTERACTION_MODE, value.toInt())
        }

    fun show(items : ArrayList<XmbMenuItem>, widthMode: PanelWidthMode = PanelWidthMode.Auto){
        this.items.clear()
        this.items.addAll(items)
        panelWidthMode = widthMode
        invalidateOrderedItems()
        selectedIndex = items.minByOrNull { it.displayOrder }?.displayOrder ?: 0
        isDisplayed = true
    }

    fun show(){
        this.items.clear()
        panelWidthMode = PanelWidthMode.Auto
        invalidateOrderedItems()
        isDisplayed = true
    }

    fun hide(){
        this.items.clear()
        panelWidthMode = PanelWidthMode.Auto
        invalidateOrderedItems()
        isDisplayed = false
    }

    private val allItems : ArrayList<XmbMenuItem>?  get(){
        if(this.items.isNotEmpty()) return this.items
        val item = vsh.hoveredItem
        return if(view.activeScreen == view.screens.mainMenu && item?.hasMenu == true){
            item.menuItems
        }else{
            this.items
        }
    }

    private fun invalidateOrderedItems() {
        orderedItemsSource = null
        orderedItemsSize = -1
        orderedItems = null
    }

    private fun orderedMenuItems(): List<XmbMenuItem>? {
        val source = allItems ?: return null
        if (source !== orderedItemsSource || source.size != orderedItemsSize) {
            orderedItemsSource = source
            orderedItemsSize = source.size
            orderedItems = source.sortedBy { it.displayOrder }
        }
        return orderedItems
    }

    fun moveCursor(isDown:Boolean){
        try{
            val menuItems = orderedMenuItems()
            if(menuItems != null && menuItems.isNotEmpty()){
                val cIndex = menuItems.indexOfFirst { it.displayOrder == selectedIndex }
                val nextIndex = (cIndex + isDown.select(1, -1)).coerceIn(0, menuItems.size - 1)

                if (cIndex != nextIndex) {
                    selectedIndex = menuItems[nextIndex].displayOrder
                    M.audio.playSfx(SfxType.Selection)
                }
            }
        }catch(_:Exception){ }
    }

    private val iconRectF = RectF(-12.0f, -12.0f, 12.0f, 12.0f)
    private val selectedItemRectF = RectF()
    private var currentPanelWidth = 400.0f

    private fun computePanelWidth(items: List<XmbMenuItem>, isPSP: Boolean): Float {
        val isWide = panelWidthMode == PanelWidthMode.Wide
        val baseWidth = when {
            isWide && isPSP -> 760.0f
            isWide -> 620.0f
            isPSP -> 400.0f
            else -> 320.0f
        }
        val maxWidth = when {
            isWide && isPSP -> (scaling.target.width() * 0.92f).coerceAtMost(1120.0f)
            isWide -> (scaling.target.width() * 0.78f).coerceAtMost(860.0f)
            isPSP -> (scaling.target.width() * 0.78f).coerceAtMost(860.0f)
            else -> (scaling.target.width() * 0.62f).coerceAtMost(640.0f)
        }
        val leftInset = isPSP.select(20.0f, textPaint.textSize + 20.0f)
        val widestItem = items.maxOfOrNull { textPaint.measureText(it.displayName) } ?: 0.0f
        val rightPadding = isWide.select(96.0f, 56.0f)
        return (leftInset + widestItem + rightPadding).coerceIn(baseWidth, maxWidth)
    }



    override fun render(ctx: Canvas) {
        showMenuDisplayFactor = (time.deltaTime * 10.0f).toLerp(showMenuDisplayFactor, isDisplayed.select(1.0f, 0.0f)).coerceIn(0.0f, 1.0f)

        if(showMenuDisplayFactor < 0.1f) return

        val isPSP = view.screens.mainMenu.layoutMode == XmbLayoutType.PSP
        textPaint.textSize = isPSP.select(30.0f, 20.0f)

        val items = orderedMenuItems()
        val itemCount = items?.size ?: 0
        if (items == null || itemCount == 0) {
            isDisplayed = false
            return
        }

        currentPanelWidth = computePanelWidth(items, isPSP)
        val menuLeft = showMenuDisplayFactor.toLerp(
            scaling.viewport.right + 10.0f,
            scaling.target.right - currentPanelWidth
        )

        itemMenuRectF.set(
            menuLeft,
            scaling.viewport.top - 10.0f,
            scaling.viewport.right + 20.0f,
            scaling.viewport.bottom + 10.0f)

        ctx.drawRect(itemMenuRectF, panelFill)

        val zeroIdx = itemMenuRectF.centerY()
        val textSize = textPaint.textSize * isPSP.select(1.5f, 1.25f)
        val textLeft = isPSP.select(0.0f, textSize) + menuLeft + 20.0f

        val selectedVisibleIndex = items.indexOfFirst { it.displayOrder == selectedIndex }.coerceAtLeast(0)
        val maxVisibleItems = ((scaling.viewport.height() / textSize).toInt() - 6).coerceAtLeast(1)
        val scrollOffset = (selectedVisibleIndex - maxVisibleItems / 2).coerceIn(0, (itemCount - maxVisibleItems).coerceAtLeast(0))

        if (scrollOffset > 0) {
            drawArrow(ctx, itemMenuRectF, true)
        }
        if (scrollOffset + maxVisibleItems < itemCount) {
            drawArrow(ctx, itemMenuRectF, false)
        }

        synchronized(items) {
            for (i in 0 until itemCount) {
                val item = items[i]
                val relativeIndex = i - scrollOffset

                // Only render if within the viewport (with small buffer)
                if (relativeIndex < -1 || relativeIndex > maxVisibleItems + 1) continue

                val isSelected = item.displayOrder == selectedIndex
                textPaint.color = when {
                    item.isDisabled -> Color.GRAY
                    isSelected -> Color.WHITE
                    else -> FColor.setAlpha(Color.WHITE, 0.5f)
                }

                val yPos = zeroIdx + ((relativeIndex - (maxVisibleItems / 2.0f)) * textSize)

                // Clamp yPos to stay within the menu background
                if (yPos < itemMenuRectF.top + 50.0f || yPos > itemMenuRectF.bottom - 50.0f) continue

                if (isSelected) {
                    if (isPSP) {
                        val pulse = ((sin(time.currentTime * 1.55f) + 1.0f) * 0.5f).toFloat()
                        selectedItemRectF.set(
                            textLeft - 5.0f, yPos - textSize,
                            scaling.viewport.right - 5.0f, yPos
                        )
                        if (showMenuDisplayFactor > 0.1f) {
                            selectedRowFill.color = Color.argb((176.0f + (pulse * 42.0f)).toInt().coerceIn(0, 255), 248, 250, 252)
                            ctx.drawRect(selectedItemRectF, selectedRowFill)
                        }
                    } else {
                        if (view.screens.mainMenu.arrowBitmapLoaded) {
                            val bitmap = view.screens.mainMenu.arrowBitmap
                            val xOff = textLeft - 20.0f
                            val yOff = yPos - (0.75f * textSize)
                            ctx.withTranslation(xOff, yOff) {
                                ctx.withRotation(180.0f) {
                                    selectedItemRectF.set(-12.0f, -12.0f, 12.0f, 12.0f)
                                    ctx.drawBitmap(bitmap, null, selectedItemRectF, null)
                                }
                            }
                        }
                    }
                }

                if (relativeIndex in 0 until maxVisibleItems) {
                    ctx.drawText(
                        item.displayName,
                        textLeft,
                        yPos,
                        textPaint,
                        -0.5f,
                        false
                    )
                }
            }
        }
    }

    private fun drawArrow(ctx: Canvas, menuRect: RectF, isUp: Boolean) {
        if (!view.screens.mainMenu.arrowBitmapLoaded) return
        val bmp = view.screens.mainMenu.arrowBitmap
        val yPosOffset = sin((view.time.currentTime * 3.0f) % (Math.PI.toFloat() * 42.0f)) * 5.0f
        val xCenter = menuRect.centerX() - 12.0f
        val yPos = if (isUp) menuRect.top + 100.0f + yPosOffset else menuRect.bottom - 100.0f - yPosOffset
        val rotation = if (isUp) 90.0f else -90.0f

        ctx.withTranslation(xCenter, yPos) {
            ctx.withRotation(rotation) {
                ctx.drawBitmap(bmp, null, iconRectF, null)
            }
        }
    }

    fun executeSelected() : Boolean {
        try{
            val items = allItems
            if(items != null && items.size > 0){
                val item = items.find {it.displayOrder == selectedIndex} ?: items.minByOrNull { it.displayOrder }
                val prevItems = items.toList()
                item?.onLaunch?.invoke()
                M.audio.playSfx(SfxType.Confirm)
                val currentItems = allItems
                return currentItems != null && currentItems.isNotEmpty() && currentItems.toList() != prevItems
            }
        }catch(_:Exception){

        }
        return false
    }

    fun onGamepadInput(key: PadKey, isDown: Boolean) : Boolean
    {
        if(isDown){
            when(key){
                PadKey.PadU -> {
                    moveCursor(false)
                    return true
                }
                PadKey.PadD -> {
                    view.widgets.sideMenu.moveCursor(true)
                    return true
                }
                PadKey.Triangle -> {
                    widgets.sideMenu.isDisplayed = false
                    return true
                }
                PadKey.Confirm, PadKey.StaticConfirm -> {
                    if(!disableMenuExec){
                        val stayOpen = view.widgets.sideMenu.executeSelected()
                        if(!stayOpen) widgets.sideMenu.isDisplayed = false
                    }
                    return true
                }
                PadKey.StaticCancel, PadKey.Cancel -> {
                    widgets.sideMenu.isDisplayed = false
                    return true
                }
                else -> { }
            }
        }else{
            when(key){
                PadKey.Confirm, PadKey.StaticConfirm -> {
                    disableMenuExec = false
                    return true
                }
                else -> { }
            }
        }
        return false
    }

    private var _hasCursorMoved = false
    fun onTouchScreen(start: PointF, current: PointF, action: Int)
    {
        when(action){
            MotionEvent.ACTION_DOWN ->{
                if(interactionMode == TouchInteractMode.Tap)
                {
                    // Is Up
                    if(current.x < itemMenuRectF.left){
                        widgets.sideMenu.isDisplayed = false
                    }else{
                        if(current.y < 200.0f){
                            view.widgets.sideMenu.moveCursor(false)
                        }else if(current.y > scaling.target.bottom - 200.0f){
                            view.widgets.sideMenu.moveCursor(true)
                        }else{
                            view.widgets.sideMenu.executeSelected()
                        }
                    }
                }
                start.set(current)
                _hasCursorMoved = false
            }
            MotionEvent.ACTION_MOVE ->
            {
                if(interactionMode == TouchInteractMode.Gesture)
                {
                    val yDiff = current.y - start.y
                    val absDiff = yDiff.absoluteValue
                    if(absDiff > 50.0f)
                    {
                        view.widgets.sideMenu.moveCursor(yDiff > 0.0f)
                        _hasCursorMoved = true
                        start.set(current)
                    }
                }
            }
            MotionEvent.ACTION_UP ->
            {
                if(interactionMode == TouchInteractMode.Gesture)
                {
                    val len = (current - start).length()
                    if(!_hasCursorMoved && len < 5.0f)
                    {
                        if(current.x < itemMenuRectF.left)
                        {
                            widgets.sideMenu.isDisplayed = false
                        }
                        else
                        {
                            view.widgets.sideMenu.executeSelected()
                        }
                    }
                }
            }
        }
    }
}
