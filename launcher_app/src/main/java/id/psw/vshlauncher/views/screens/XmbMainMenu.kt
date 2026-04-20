package id.psw.vshlauncher.views.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.contains
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.withRotation
import androidx.core.graphics.withScale
import androidx.core.graphics.withTranslation
import id.psw.vshlauncher.Consts
import id.psw.vshlauncher.FColor
import id.psw.vshlauncher.FittingMode
import id.psw.vshlauncher.PrefEntry
import id.psw.vshlauncher.R
import id.psw.vshlauncher.Vsh
import id.psw.vshlauncher.copyList
import id.psw.vshlauncher.hasConcurrentLoading
import id.psw.vshlauncher.lerpFactor
import id.psw.vshlauncher.livewallpaper.NativeGL
import id.psw.vshlauncher.livewallpaper.XMBWaveRenderer
import id.psw.vshlauncher.livewallpaper.XMBWaveSurfaceView
import id.psw.vshlauncher.makeTextPaint
import id.psw.vshlauncher.select
import id.psw.vshlauncher.submodules.PadKey
import id.psw.vshlauncher.submodules.SfxType
import id.psw.vshlauncher.toLerp
import id.psw.vshlauncher.types.CifLoader
import id.psw.vshlauncher.types.VideoIconMode
import id.psw.vshlauncher.types.XmbItem
import id.psw.vshlauncher.types.items.XmbAppItem
import id.psw.vshlauncher.types.items.XmbCustomLaunchItem
import id.psw.vshlauncher.types.items.XmbItemCategory
import id.psw.vshlauncher.types.items.XmbSettingsCategory
import id.psw.vshlauncher.typography.FontCollections
import id.psw.vshlauncher.views.DirectionLock
import id.psw.vshlauncher.views.DrawExtension
import id.psw.vshlauncher.views.XmbLayoutType
import id.psw.vshlauncher.views.XmbScreen
import id.psw.vshlauncher.views.XmbView
import id.psw.vshlauncher.views.drawBitmap
import id.psw.vshlauncher.views.drawText
import id.psw.vshlauncher.views.filterBySearch
import id.psw.vshlauncher.views.nativedlg.NativeEditTextDialog
import id.psw.vshlauncher.visibleItems
import id.psw.vshlauncher.vsh
import id.psw.vshlauncher.xmb
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

class XmbMainMenu(view : XmbView) : XmbScreen(view)  {
    companion object {
        private const val PSP_CANON_WIDTH = 480.0f
        private const val PSP_CANON_HEIGHT = 272.0f
        private const val PSP_CANVAS_Y_OFFSET = 26.5f
        private const val WIDE_CARD_ASPECT_THRESHOLD = 1.45f
        private const val WIDE_CARD_UNSELECTED_HEIGHT_FACTOR = 0.78f
        private val PSP_MEMORY_STICK_ROW_IDS = setOf(
            "photo_ms",
            "music_ms",
            "video_ms",
            "game_ms",
            Vsh.NODE_APPS_MEMORY_STICK
        )
        private val PSP_STORAGE_CONTENT_PARENT_IDS = setOf(
            "game_saved_data",
            "game_ms",
            Vsh.NODE_APPS_MEMORY_STICK
        )
        private val PSP_MEDIA_SOURCE_PARENT_IDS = setOf(
            "photo_ms",
            "music_ms",
            "video_ms"
        )
        private const val STORAGE_SPINNER_MIN_DWELL = 0.2f
        private const val STORAGE_SPINNER_MAX_DWELL = 1.0f
        private const val ANIMATED_ICON_SELECTED_DWELL = 1.0f
    }

    private enum class PspRowPresentationMode {
        SettingsList,
        SettingsDetailList,
        StorageContentList,
        MediaSourceList,
        MediaContentList,
        ContentList
    }

    private fun PspRowPresentationMode.isSettingsListMode(): Boolean {
        return this == PspRowPresentationMode.SettingsList ||
            this == PspRowPresentationMode.SettingsDetailList
    }

    var sortHeaderDisplay: Float = 0.0f
    var layoutMode : XmbLayoutType = XmbLayoutType.PS3
    lateinit var arrowBitmap : Bitmap
    var arrowBitmapLoaded = false
    var menuScaleTime : Float = 0.0f
    var loadingIconBitmap : Bitmap? = null
    var coldBootTransition = 2.0f
    var coldBootWaveVerticalScale = 0.1f;
    var dimOpacity = 0
    private var storageSpinnerVisible = false
    private var storageSpinnerStartedAt = 0.0f
    private var storageSpinnerNodeId = ""
    private var appOptionsHintItemId = ""
    private var appOptionsHintStartedAt = 0.0f
    private var animatedIconSelectedItemId = ""
    private var animatedIconSelectedStartedAt = 0.0f
    private var backdropFadeActive = false

    data class VerticalMenu(
            var playAnimatedIcon : Boolean = true,
            var playBackSound : Boolean = true,
            var showBackdrop : Boolean = true,
            var nameTextXOffset : Float = 0.0f,
            var descTextXOffset : Float = 0.0f
    )

    // val dateTimeFormat = "dd/M HH:mm a"
    val verticalMenu = VerticalMenu()
    private var directionLock : DirectionLock = DirectionLock.None
    private val backgroundPaint : Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sortHeaderOutlinePaint : Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.0f
        color = Color.WHITE
    }
    private val sortHeaderFillPaint : Paint = vsh.makeTextPaint(color = FColor.setAlpha(Color.BLACK, 0.5f)).apply {
        style = Paint.Style.FILL
        strokeWidth = 3.0f
    }
    private val sortHeaderTextPaint : Paint = vsh.makeTextPaint(size = 20.0f, color = Color.WHITE).apply {
        style = Paint.Style.FILL
        strokeWidth = 3.0f
    }
    private val iconPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {}
    private val menuVerticalNamePaint : Paint = vsh.makeTextPaint(size = 20.0f, color = Color.WHITE).apply {
        textAlign = Paint.Align.LEFT
    }
    private val menuVerticalDescPaint : Paint = vsh.makeTextPaint(size = 10.0f, color = Color.WHITE).apply {
        textAlign = Paint.Align.LEFT
    }

    private val menuHorizontalNamePaint : Paint = vsh.makeTextPaint(size = 15.0f, color = Color.WHITE).apply {
        style = Paint.Style.FILL
        textAlign = Paint.Align.CENTER
    }
    private val menuHorizontalIconPaint : Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        alpha = 255
    }
    private val selectedIconGlowPaint : Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val selectedIconRingPaint : Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val appHintBgPaint : Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(0, 58, 62, 68)
    }
    private val appHintTextPaint : Paint = vsh.makeTextPaint(size = 16.0f, color = Color.WHITE).apply {
        textAlign = Paint.Align.LEFT
    }
    private val appHintButtonPaint : Paint = vsh.makeTextPaint(size = 20.0f, color = Color.WHITE).apply {
        textAlign = Paint.Align.LEFT
        typeface = FontCollections.buttonFont
    }
    
    private var baseDefRect = RectF()
    private var tmpRectF = RectF()
    private var glowRectF = RectF()
    private var tmpPointFA = PointF()
    private var tmpPointFB = PointF()
    private val tmpPath = Path()

    /** Is **Open Menu Hold** enabled. */
    var isOpenMenuOnHold = true
    var isOpenMenuHeld = false

    override fun start() {
        currentTime = 0.0f
        menuScaleTime = 1.0f

        if(!arrowBitmapLoaded){
            arrowBitmap =
                    ResourcesCompat.getDrawable(view.resources, R.drawable.miptex_arrow, null)
                            ?.toBitmap(128,128)
                            ?: XmbItem.TRANSPARENT_BITMAP
            arrowBitmapLoaded = true
        }

        if(loadingIconBitmap == null){
            loadingIconBitmap = ResourcesCompat.getDrawable(context.resources, R.drawable.ic_sync_loading,null)?.toBitmap(256,256)
        }

        widgets.statusBar.disabled = M.pref.get(PrefEntry.DISPLAY_DISABLE_STATUS_BAR, 0) == 1
        widgets.analogClock.showSecondHand = M.pref.get(PrefEntry.DISPLAY_SHOW_CLOCK_SECOND, 0) == 1

        val waveType = M.pref.get(XMBWaveSurfaceView.KEY_STYLE, XMBWaveRenderer.WAVE_TYPE_PS3_BLINKS.toInt()).toByte()
        if(waveType == XMBWaveRenderer.WAVE_TYPE_PSP_CENTER){
            coldBootWaveVerticalScale = 0.5f;
        }
    }

    override fun prefersFullFrameRate(): Boolean {
        val hoveredItem = context.vsh.hoveredItem
        val hasActiveAnimatedPreview = CifLoader.videoIconMode != VideoIconMode.Disabled &&
                hoveredItem?.hasAnimatedIcon == true &&
                hoveredItem.isAnimatedIconLoaded
        val appOptionsHintActive = appOptionsHintItemId.isNotBlank() &&
                currentTime - appOptionsHintStartedAt < 3.6f

        return context.vsh.hasConcurrentLoading ||
                vsh.M.media.isListingInProgress ||
                storageSpinnerVisible ||
                sortHeaderDisplay > 0.0f ||
                menuScaleTime > 0.01f ||
                coldBootTransition > -1.0f ||
                abs(context.vsh.itemOffsetX) > 0.01f ||
                abs(context.vsh.itemOffsetY) > 0.01f ||
                backdropFadeActive ||
                widgets.sideMenu.isDisplayed ||
                widgets.sideMenu.showMenuDisplayFactor > 0.01f ||
                appOptionsHintActive ||
                hasActiveAnimatedPreview
    }

    private val ps3MenuIconCenter = PointF(0.30f, 0.25f)
    private val ps3SelectedCategoryIconSize = PointF(75.0f, 75.0f)
    private val ps3UnselectedCategoryIconSize = PointF(60.0f, 60.0f)

    // PS3 Base Icon Aspect Ratio = 20:11 (320x176)
    private val ps3SelectedIconSize = PointF(200.0f, 110.0f)
    private val ps3UnselectedIconSize = PointF(120.0f, 66.0f)

    private val ps3IconSeparation = PointF(150.0f, 70.0f)
    private val horizontalRectF = RectF()

    private val verticalRectF = RectF()
    private val textNameRectF = RectF()

    private data class PspVirtualMetrics(
        val scale: Float,
        val left: Float,
        val top: Float
    ) {
        fun x(value: Float) = left + (value * scale)
        fun y(value: Float) = top + (value * scale)
        fun s(value: Float) = value * scale

        val categoryCenter = PointF(x(111.0f), y(50.0f))
        val categorySpacingX = s(76.0f)
        val categorySelectedSize = PointF(s(68.5f), s(51.4f))
        val categoryUnselectedSize = PointF(s(60.0f), s(45.0f))
        val categoryRaise = s(3.0f)
        val categoryEdgeNudge = s(4.0f)
        val categoryLabelSize = s(10.5f)

        val firstColumnCenter = PointF(x(109.0f), y(109.0f))
        val secondColumnCenter = PointF(x(165.0f), y(109.0f))
        val itemSlideX = s(48.0f)
        val itemSpacingY = s(40.0f)
        val firstLevelSelectedSize = PointF(s(43.6f), s(43.6f))
        val firstLevelUnselectedSize = PointF(s(43.0f), s(43.0f))
        val secondLevelSelectedSize = PointF(s(29.0f), s(29.0f))
        val secondLevelUnselectedSize = PointF(s(29.0f), s(29.0f))
        val settingsDetailColumnCenter = PointF(x(118.0f), y(109.0f))
        val storageContentSelectedSize = PointF(s(93.6f), s(70.2f))
        val storageContentUnselectedSize = PointF(s(60.0f), s(45.0f))
        val mediaContentItemSize = PointF(s(72.0f), s(46.0f))
        val firstLevelFocusSize = PointF(s(64.0f), s(64.0f))
        val secondLevelFocusSize = PointF(s(48.0f), s(48.0f))
        val storageContentFocusSize = PointF(s(103.2f), s(77.4f))
        val breadcrumbArrowOffset = s(72.0f)
        val breadcrumbStep = s(48.0f)
        val textGap = s(16.0f)
        val emptyStateTextGap = s(22.0f)
        val contentRight = x(476.0f)
        val valueLeft = x(356.0f)
        val rootNameSize = s(13.2f)
        val rootDescSize = s(9.2f)
        val nestedNameSize = s(10.8f)
        val nestedDescSize = s(7.4f)
        val storageContentNameSize = s(12.4f)
        val storageContentDescSize = s(8.6f)
        val memoryStickDividerOffsetY = s(1.6f)
    }

    private fun pspMetrics(): PspVirtualMetrics {
        val scale = scaling.target.height() / PSP_CANON_HEIGHT
        val layoutWidth = PSP_CANON_WIDTH * scale
        val left = scaling.target.left + ((scaling.target.width() - layoutWidth) / 2.0f)
        return PspVirtualMetrics(scale, left, scaling.target.top + (PSP_CANVAS_Y_OFFSET * scale))
    }

    fun showStorageNodeLoadingSpinner(nodeId: String) {
        storageSpinnerVisible = true
        storageSpinnerStartedAt = currentTime
        storageSpinnerNodeId = nodeId
    }

    private fun shouldHideStorageSpinner(): Boolean {
        if (!storageSpinnerVisible) return true
        val elapsed = currentTime - storageSpinnerStartedAt
        if (elapsed >= STORAGE_SPINNER_MAX_DWELL) return true

        val activeParentId = context.vsh.activeParent?.id
        if (activeParentId != storageSpinnerNodeId) return true

        val mediaBusy = vsh.M.media.isListingInProgress
        val appBusy = vsh.hasConcurrentLoading
        return elapsed >= STORAGE_SPINNER_MIN_DWELL && !mediaBusy && !appBusy
    }

    private fun drawStorageNodeSpinner(ctx: Canvas) {
        val loadIcon = loadingIconBitmap ?: return
        if (shouldHideStorageSpinner()) {
            storageSpinnerVisible = false
            storageSpinnerNodeId = ""
            return
        }

        val psp = if (layoutMode == XmbLayoutType.PSP) pspMetrics() else null
        val size = psp?.s(17.4f) ?: 40.0f
        val margin = psp?.s(10.6f) ?: 22.0f
        tmpRectF.set(
            scaling.viewport.right - margin - size,
            scaling.viewport.bottom - margin - size,
            scaling.viewport.right - margin,
            scaling.viewport.bottom - margin
        )
        iconPaint.alpha = 200
        ctx.withRotation(
            ((time.currentTime + 0.375f) * -360.0f) % 360.0f,
            tmpRectF.centerX(),
            tmpRectF.centerY()
        ) {
            ctx.drawBitmap(loadIcon, null, tmpRectF, iconPaint, FittingMode.FIT, 0.5f, 0.5f)
        }
    }

    private fun drawBackground(ctx:Canvas){

        val isPortrait = scaling.screen.height() > scaling.screen.width()
        val activeItem = vsh.items?.visibleItems?.find{it.id == context.vsh.selectedItemId}
        var isChanged = false
        val opa = dimOpacity / 10.0f
        val launcherWallpaper = vsh.M.customizer.activeLauncherWallpaper

        // Launcher wallpaper is a persistent base layer. If it is reset/empty, draw nothing here
        // so the device wallpaper remains visible through the transparent launcher window.
        if (launcherWallpaper != null && launcherWallpaper.isLoaded) {
            backgroundPaint.alpha = 255
            ctx.drawBitmap(launcherWallpaper.bitmap, null, scaling.viewport, backgroundPaint, FittingMode.FILL, 0.5f, 0.5f)
        }

        if(verticalMenu.showBackdrop && activeItem != null){
            try{
                context.vsh.itemBackdropAlphaTime =context.vsh.itemBackdropAlphaTime.coerceIn(0f, 1f)
                backgroundPaint.alpha = (context.vsh.itemBackdropAlphaTime * 255).roundToInt().coerceIn(0, 255)
                if(isPortrait){
                    val themePort = vsh.M.customizer.activeThemePortraitBackdrop
                    if(activeItem.hasPortraitBackdrop && activeItem.isPortraitBackdropLoaded){
                        ctx.drawBitmap(
                            activeItem.portraitBackdrop,
                            null,
                            scaling.viewport,
                            backgroundPaint,
                            FittingMode.FILL, 0.5f, 0.5f)
                        isChanged = true
                    } else if (themePort != null && themePort.isLoaded) {
                        ctx.drawBitmap(themePort.bitmap, null, scaling.viewport, backgroundPaint, FittingMode.FILL, 0.5f, 0.5f)
                        isChanged = true
                    }
                }else {
                    val themeLand = vsh.M.customizer.activeThemeBackdrop
                    if(activeItem.hasBackdrop && activeItem.isBackdropLoaded){
                        ctx.drawBitmap(
                            activeItem.backdrop,
                            null,
                            scaling.viewport,
                            backgroundPaint,
                            FittingMode.FILL, 0.5f, 0.5f)
                        isChanged = true
                    } else if (themeLand != null && themeLand.isLoaded) {
                        ctx.drawBitmap(themeLand.bitmap, null, scaling.viewport, backgroundPaint, FittingMode.FILL, 0.5f, 0.5f)
                        isChanged = true
                    }
                }
            }catch(cme:ConcurrentModificationException){
                cme.printStackTrace()
            }
        }

        val focusAlpha = widgets.sideMenu.showMenuDisplayFactor.toLerp(0f, 128f).toInt()
        ctx.drawARGB(focusAlpha, 0,0,0)

        if(isChanged){
            ctx.drawARGB((context.vsh.itemBackdropAlphaTime * opa * 255).toInt(), 0,0,0)
            if(context.vsh.itemBackdropAlphaTime < 1.0f) context.vsh.itemBackdropAlphaTime += (time.deltaTime) * 5.0f
        }
        backdropFadeActive = isChanged && context.vsh.itemBackdropAlphaTime < 1.0f

        if(verticalMenu.showBackdrop && activeItem != null){
            try{
                if(isPortrait){
                    if(activeItem.hasPortraitBackdropOverlay && activeItem.isPortraitBackdropOverlayLoaded){
                        ctx.drawBitmap(
                            activeItem.portraitBackdropOverlay,
                            null,
                            scaling.viewport,
                            backgroundPaint,
                            FittingMode.FIT, 0.5f, 0.5f)
                    }
                }else{
                    if(activeItem.hasBackOverlay && activeItem.isBackdropOverlayLoaded) {
                        ctx.drawBitmap(
                            activeItem.backdropOverlay,
                            null,
                            scaling.viewport,
                            backgroundPaint,
                            FittingMode.FIT, 0.5f, 0.5f)
                    }
                }
            }catch(cme:ConcurrentModificationException){
                cme.printStackTrace()
            }
        }

    }

    private fun drawHorizontalMenu(ctx:Canvas) {
        val isPSP = layoutMode == XmbLayoutType.PSP
        val psp = if (isPSP) pspMetrics() else null
        val pspRowMode = if (psp != null) getPspRowPresentationMode() else PspRowPresentationMode.ContentList
        if (psp != null && pspRowMode == PspRowPresentationMode.StorageContentList) {
            drawPspContentCategoryMarker(ctx, psp)
            return
        }
        if (psp != null && pspRowMode == PspRowPresentationMode.MediaContentList) {
            drawPspMediaSourceCategoryContext(ctx, psp)
            return
        }
        if (psp != null && pspRowMode == PspRowPresentationMode.SettingsDetailList) {
            drawPspSettingsCategoryContext(ctx, psp)
            return
        }
        val pspNormalNodeContext = psp != null && !context.vsh.isInRoot
        val center = ps3MenuIconCenter
        val xPos = if (psp != null) {
            psp.categoryCenter.x
        } else {
            (scaling.target.width() * center.x) + context.vsh.isInRoot.select(0f, ps3SelectedIconSize.x * -0.75f)
        }
        val yPos = if (psp != null) {
            psp.categoryCenter.y
        } else {
            scaling.target.height() * center.y
        }
        val notHidden = context.vsh.categories.copyList().visibleItems
        val separation = psp?.categorySpacingX ?: ps3IconSeparation.x
        val cursorX = context.vsh.itemCursorX
        for(wx in notHidden.indices){
            val item = notHidden[wx]
            val ix = wx - cursorX
            val selected = ix == 0

            val targetSize =
                    if (psp != null) {
                        selected.select(psp.categorySelectedSize, psp.categoryUnselectedSize)
                    } else {
                        selected.select(ps3SelectedCategoryIconSize, ps3UnselectedCategoryIconSize)
                    }
            var size = targetSize

            if(selected){
                val sizeTransition = abs(context.vsh.itemOffsetX)
                val previousSize =
                        if (psp != null) {
                            selected.select(psp.categoryUnselectedSize, psp.categorySelectedSize)
                        } else {
                            selected.select(ps3UnselectedCategoryIconSize, ps3SelectedCategoryIconSize)
                        }
                tmpPointFA.x = sizeTransition.toLerp(targetSize.x, previousSize.x)
                tmpPointFA.y = sizeTransition.toLerp(targetSize.y, previousSize.y)
                size = tmpPointFA
                size = PointF(size.x * 1.05f, size.y * 1.05f)

            }

            val hSizeX = size.x / 2.0f
            val hSizeY = (size.y / 2.0f)
            var centerX = xPos + ((ix + context.vsh.itemOffsetX) * separation)
            var centerDrawY = yPos

            val isInViewport = centerX > scaling.viewport.left && centerX < scaling.viewport.right

            item.screenVisibility = isInViewport

            if(isInViewport){
                if(selected) centerDrawY -= psp?.categoryRaise ?: 4.0f

                if(ix < 0) centerX -= psp?.categoryEdgeNudge ?: 10.0f
                if(ix > 0) centerX += psp?.categoryEdgeNudge ?: 10.0f
                horizontalRectF.set(centerX - hSizeX, centerDrawY - hSizeY, centerX + hSizeX, centerDrawY + hSizeY)
                if(selected) drawActiveCategoryFocus(ctx, horizontalRectF, isPSP)
                menuHorizontalIconPaint.alpha = when {
                    selected -> 255
                    pspNormalNodeContext -> 128
                    else -> context.vsh.isInRoot.select(200, 0)
                }
                ctx.drawBitmap(item.icon, null, horizontalRectF, menuHorizontalIconPaint, FittingMode.FIT, 0.5f, 1.0f)
                if(selected && (context.vsh.isInRoot || pspNormalNodeContext)){
                    val iconCtrToText = psp?.categorySelectedSize?.y ?: ps3SelectedCategoryIconSize.y
                    val categoryLabelYOffset = psp?.s(-4.5f) ?: 0.0f
                    menuHorizontalNamePaint.textSize = psp?.categoryLabelSize ?: 15f
                    ctx.drawText(item.displayName, centerX, centerDrawY + (iconCtrToText / 2.0f) + categoryLabelYOffset, menuHorizontalNamePaint, 1.0f)
                }
            }
        }
    }

    private fun drawPspContentCategoryMarker(ctx: Canvas, psp: PspVirtualMetrics) {
        val category = context.vsh.categories.visibleItems.find { it.id == context.vsh.selectedCategoryId } ?: return
        val width = psp.categorySelectedSize.x
        val height = psp.categorySelectedSize.y
        val left = scaling.viewport.left - (width * 0.54f)
        val centerY = psp.categoryCenter.y
        horizontalRectF.set(left, centerY - (height * 0.5f), left + width, centerY + (height * 0.5f))
        menuHorizontalIconPaint.alpha = 226
        ctx.drawBitmap(category.icon, null, horizontalRectF, menuHorizontalIconPaint, FittingMode.FIT, 0.5f, 1.0f)
    }

    private fun drawPspMediaSourceCategoryContext(ctx: Canvas, psp: PspVirtualMetrics) {
        val category = context.vsh.categories.visibleItems.find { it.id == context.vsh.selectedCategoryId } ?: return
        val previousSource = getActivePspMediaSourcePreviousItem()
        val contextCenterX = scaling.viewport.left + psp.s(72.0f)

        previousSource?.let { item ->
            val width = psp.firstLevelUnselectedSize.x
            val height = psp.firstLevelUnselectedSize.y
            val centerY = scaling.viewport.top + (height * 0.25f)
            horizontalRectF.set(contextCenterX - (width * 0.5f), centerY - (height * 0.5f), contextCenterX + (width * 0.5f), centerY + (height * 0.5f))
            menuHorizontalIconPaint.alpha = 128
            if (item.hasIcon) {
                ctx.drawBitmap(item.icon, null, horizontalRectF, menuHorizontalIconPaint, FittingMode.FIT, 0.5f, 1.0f)
            }
        }

        val width = psp.categorySelectedSize.x
        val height = psp.categorySelectedSize.y
        val centerY = psp.categoryCenter.y
        horizontalRectF.set(contextCenterX - (width * 0.5f), centerY - (height * 0.5f), contextCenterX + (width * 0.5f), centerY + (height * 0.5f))
        menuHorizontalIconPaint.alpha = 255
        ctx.drawBitmap(category.icon, null, horizontalRectF, menuHorizontalIconPaint, FittingMode.FIT, 0.5f, 1.0f)

        menuHorizontalNamePaint.textSize = psp.categoryLabelSize
        val categoryLabelYOffset = psp.s(-4.5f)
        ctx.drawText(category.displayName, contextCenterX, centerY + (height * 0.5f) + categoryLabelYOffset, menuHorizontalNamePaint, 1.0f)
    }

    private fun drawPspSettingsCategoryContext(ctx: Canvas, psp: PspVirtualMetrics) {
        val category = context.vsh.categories.visibleItems.find { it.id == context.vsh.selectedCategoryId } ?: return
        val contextCenterX = scaling.viewport.left + psp.s(72.0f)
        val centerY = psp.categoryCenter.y
        val width = psp.categorySelectedSize.x
        val height = psp.categorySelectedSize.y

        horizontalRectF.set(
            contextCenterX - (width * 0.5f),
            centerY - (height * 0.5f),
            contextCenterX + (width * 0.5f),
            centerY + (height * 0.5f)
        )
        menuHorizontalIconPaint.alpha = 255
        ctx.drawBitmap(category.icon, null, horizontalRectF, menuHorizontalIconPaint, FittingMode.FIT, 0.5f, 1.0f)

        menuHorizontalNamePaint.textSize = psp.categoryLabelSize
        val categoryLabelYOffset = psp.s(-4.5f)
        ctx.drawText(category.displayName, contextCenterX, centerY + (height * 0.5f) + categoryLabelYOffset, menuHorizontalNamePaint, 1.0f)
    }

    private fun drawPspSettingsDetailNodeContext(ctx: Canvas, psp: PspVirtualMetrics, centerY: Float) {
        val category = context.vsh.categories.visibleItems.find { it.id == context.vsh.selectedCategoryId } ?: return
        val activeParent = context.vsh.activeParent ?: return
        val nodes = category.content?.visibleItems ?: return
        val parentIndex = nodes.indexOfFirst { it.id == activeParent.id }
        if (parentIndex < 0) return

        val centerX = scaling.viewport.left + psp.s(72.0f)
        nodes.forEachIndexed { index, item ->
            val relativeIndex = index - parentIndex
            if (relativeIndex < -1 || relativeIndex > 2) return@forEachIndexed

            val selected = relativeIndex == 0
            val size = selected.select(psp.firstLevelSelectedSize, psp.firstLevelUnselectedSize)
            val rowCenterY = if (relativeIndex == -1) {
                scaling.viewport.top + (size.y * 0.30f)
            } else {
                centerY + (relativeIndex * psp.itemSpacingY)
            }

            if ((rowCenterY + (size.y * 0.5f)) <= scaling.viewport.top ||
                (rowCenterY - (size.y * 0.5f)) >= scaling.viewport.bottom
            ) {
                return@forEachIndexed
            }

            iconPaint.alpha = selected.select(230, 92)
            verticalRectF.set(
                centerX - (size.x * 0.5f),
                rowCenterY - (size.y * 0.5f),
                centerX + (size.x * 0.5f),
                rowCenterY + (size.y * 0.5f)
            )
            if (item.hasIcon) {
                ctx.drawBitmap(item.icon, null, verticalRectF, iconPaint, FittingMode.FIT, 0.5f, 0.5f)
            }
        }
    }

    private fun drawActiveCategoryFocus(ctx: Canvas, rect: RectF, isPSP: Boolean) {
        if (isPSP) {
            return
        }
        val pspScale = if (isPSP) scaling.target.height() / PSP_CANON_HEIGHT else 1.0f
        val shadowExpand = isPSP.select(4.0f * pspScale, 7.0f)
        glowRectF.set(
            rect.left - shadowExpand,
            rect.top - shadowExpand + isPSP.select(1.8f * pspScale, 3.0f),
            rect.right + shadowExpand,
            rect.bottom + shadowExpand + isPSP.select(1.8f * pspScale, 3.0f)
        )
        selectedIconGlowPaint.color = Color.argb(isPSP.select(88, 72), 0, 0, 0)
        ctx.drawRoundRect(glowRectF, glowRectF.height() * 0.2f, glowRectF.height() * 0.2f, selectedIconGlowPaint)
    }

    private fun drawPspContentListGlow(ctx: Canvas, rect: RectF) {
        val pulse = ((sin(currentTime * 1.82f) + 1.0f) * 0.5f).toFloat()
        val centerX = rect.centerX()
        val centerY = rect.centerY()
        val rectAspect = rect.width() / rect.height().coerceAtLeast(1.0f)
        val breathScale = 0.98f + (pulse * 0.045f)
        val haloWidthFactor = if (rectAspect >= WIDE_CARD_ASPECT_THRESHOLD) 1.04f else 1.22f
        val haloHeightFactor = if (rectAspect >= WIDE_CARD_ASPECT_THRESHOLD) 1.18f else 1.22f
        val haloWidth = rect.width() * (haloWidthFactor + (pulse * 0.025f))
        val haloHeight = rect.height() * (haloHeightFactor + (pulse * 0.04f))
        val radius = haloHeight * 0.5f
        val xScale = haloWidth / haloHeight.coerceAtLeast(1.0f)
        val haloAlpha = (42.0f + (pulse * 13.0f)).roundToInt().coerceIn(0, 255)

        selectedIconGlowPaint.shader = RadialGradient(
            centerX,
            centerY,
            radius * breathScale,
            intArrayOf(
                Color.argb(haloAlpha, 255, 255, 255),
                Color.argb((haloAlpha * 0.42f).roundToInt(), 255, 255, 255),
                Color.TRANSPARENT
            ),
            floatArrayOf(0.0f, 0.50f, 1.0f),
            Shader.TileMode.CLAMP
        )
        ctx.withScale(xScale, 1.0f, centerX, centerY) {
            ctx.drawCircle(centerX, centerY, radius * breathScale, selectedIconGlowPaint)
        }
        selectedIconGlowPaint.shader = null
    }

    private fun drawSelectedIconGlow(ctx: Canvas, rect: RectF, isPSP: Boolean, intensity: Float = 1.0f) {
        val pulse = ((sin(currentTime * 1.35f) + 1.0f) * 0.5f).toFloat()
        if (isPSP) {
            val pspPulse = ((sin(currentTime * 1.82f) + 1.0f) * 0.5f).toFloat()
            val centerX = rect.centerX()
            val centerY = rect.centerY()
            val rectAspect = rect.width() / rect.height().coerceAtLeast(1.0f)
            if (rectAspect >= WIDE_CARD_ASPECT_THRESHOLD) {
                val breathScale = 0.97f + (pspPulse * 0.06f)
                val expandX = rect.height() * (0.12f + (pspPulse * 0.03f))
                val expandY = rect.height() * (0.11f + (pspPulse * 0.025f))
                val haloAlpha = ((26.0f + (pspPulse * 10.0f)) * intensity).roundToInt().coerceIn(0, 255)

                glowRectF.set(
                    centerX - (rect.width() * 0.5f * breathScale) - expandX,
                    centerY - (rect.height() * 0.5f * breathScale) - expandY,
                    centerX + (rect.width() * 0.5f * breathScale) + expandX,
                    centerY + (rect.height() * 0.5f * breathScale) + expandY
                )
                val ovalRadius = glowRectF.height() * 0.5f
                val xScale = glowRectF.width() / glowRectF.height().coerceAtLeast(1.0f)
                selectedIconGlowPaint.shader = RadialGradient(
                    centerX,
                    centerY,
                    ovalRadius,
                    intArrayOf(
                        Color.argb(haloAlpha, 255, 255, 255),
                        Color.argb((haloAlpha * 0.36f).roundToInt(), 255, 255, 255),
                        Color.TRANSPARENT
                    ),
                    floatArrayOf(0.0f, 0.54f, 1.0f),
                    Shader.TileMode.CLAMP
                )
                ctx.withScale(xScale, 1.0f, centerX, centerY) {
                    ctx.drawCircle(centerX, centerY, ovalRadius, selectedIconGlowPaint)
                }
                selectedIconGlowPaint.shader = null
                return
            }
            val iconSize = maxOf(rect.width(), rect.height())
            val breathScale = 0.96f + (pspPulse * 0.08f)
            val haloRadius = iconSize * 0.51f * breathScale
            val coreRadius = iconSize * 0.36f * breathScale
            val haloAlpha = ((36.0f + (pspPulse * 12.0f)) * intensity).roundToInt().coerceIn(0, 255)
            val coreAlpha = ((22.0f + (pspPulse * 8.5f)) * intensity).roundToInt().coerceIn(0, 255)

            selectedIconGlowPaint.shader = RadialGradient(
                centerX,
                centerY,
                haloRadius,
                intArrayOf(
                    Color.argb(haloAlpha, 255, 255, 255),
                    Color.argb((haloAlpha * 0.42f).roundToInt(), 255, 255, 255),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0.0f, 0.48f, 1.0f),
                Shader.TileMode.CLAMP
            )
            ctx.drawCircle(centerX, centerY, haloRadius, selectedIconGlowPaint)

            selectedIconGlowPaint.shader = RadialGradient(
                centerX,
                centerY,
                coreRadius,
                intArrayOf(
                    Color.argb(coreAlpha, 255, 255, 255),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0.0f, 1.0f),
                Shader.TileMode.CLAMP
            )
            ctx.drawCircle(centerX, centerY, coreRadius, selectedIconGlowPaint)
            selectedIconGlowPaint.shader = null
            return
        }
        val outerExpand = isPSP.select(5.0f, 3.8f) + (pulse * isPSP.select(1.1f, 0.8f))
        val innerExpand = isPSP.select(1.8f, 1.2f) + (pulse * isPSP.select(0.45f, 0.3f))
        val haloAlpha = ((10.0f + (pulse * 8.0f)) * intensity).roundToInt().coerceIn(0, 255)
        val ringAlpha = ((24.0f + (pulse * 12.0f)) * intensity).roundToInt().coerceIn(0, 255)

        glowRectF.set(
            rect.left - outerExpand,
            rect.top - outerExpand,
            rect.right + outerExpand,
            rect.bottom + outerExpand
        )
        selectedIconGlowPaint.color = Color.argb(haloAlpha, 255, 255, 255)
        ctx.drawRoundRect(glowRectF, glowRectF.height() * 0.18f, glowRectF.height() * 0.18f, selectedIconGlowPaint)

        glowRectF.set(
            rect.left - innerExpand,
            rect.top - innerExpand,
            rect.right + innerExpand,
            rect.bottom + innerExpand
        )
        selectedIconRingPaint.strokeWidth = isPSP.select(1.55f, 1.25f)
        selectedIconRingPaint.color = Color.argb(ringAlpha, 255, 255, 255)
        ctx.drawRoundRect(glowRectF, glowRectF.height() * 0.16f, glowRectF.height() * 0.16f, selectedIconRingPaint)
    }

    private fun isPspWideCardItem(item: XmbItem): Boolean {
        if (layoutMode != XmbLayoutType.PSP || !item.isIconLoaded) return false
        if (item !is XmbAppItem && item !is XmbCustomLaunchItem) return false
        val iconHeight = item.icon.height.coerceAtLeast(1)
        return item.icon.width.toFloat() / iconHeight.toFloat() >= WIDE_CARD_ASPECT_THRESHOLD
    }

    private fun applyPspWideCardBounds(item: XmbItem, selected: Boolean, baseHeight: Float, centerX: Float, centerY: Float): Boolean {
        if (!isPspWideCardItem(item)) return false
        val iconAspect = item.icon.width.toFloat() / item.icon.height.coerceAtLeast(1).toFloat()
        val displayHeight = baseHeight * selected.select(1.0f, WIDE_CARD_UNSELECTED_HEIGHT_FACTOR)
        val displayWidth = displayHeight * iconAspect
        verticalRectF.set(
            centerX - (displayWidth * 0.5f),
            centerY - (displayHeight * 0.5f),
            centerX + (displayWidth * 0.5f),
            centerY + (displayHeight * 0.5f)
        )
        return true
    }

    private fun getPspRowPresentationMode(): PspRowPresentationMode {
        if (layoutMode != XmbLayoutType.PSP) return PspRowPresentationMode.ContentList
        val activeParent = context.vsh.activeParent
        val mediaSourceStackIndex = getActivePspMediaSourceStackIndex()
        return when {
            context.vsh.selectedCategoryId == Vsh.ITEM_CATEGORY_SETTINGS && !context.vsh.isInRoot -> PspRowPresentationMode.SettingsDetailList
            context.vsh.selectedCategoryId == Vsh.ITEM_CATEGORY_SETTINGS -> PspRowPresentationMode.SettingsList
            activeParent is XmbSettingsCategory -> PspRowPresentationMode.SettingsDetailList
            activeParent?.id in PSP_STORAGE_CONTENT_PARENT_IDS -> PspRowPresentationMode.StorageContentList
            getSelectedPspMediaSourceId() != null -> PspRowPresentationMode.MediaSourceList
            mediaSourceStackIndex != null -> PspRowPresentationMode.MediaContentList
            else -> PspRowPresentationMode.ContentList
        }
    }

    private fun getSelectedPspMediaSourceId(): String? {
        val selectedId = context.vsh.selectedItemId
        return if (context.vsh.isInRoot && selectedId in PSP_MEDIA_SOURCE_PARENT_IDS) selectedId else null
    }

    private fun getActivePspMediaSourceStackIndex(): Int? {
        val stack = context.vsh.selectStack
        for (i in 0 until stack.size) {
            val id = stack[i]
            if (id in PSP_MEDIA_SOURCE_PARENT_IDS) return i
        }
        return null
    }

    private fun getActivePspMediaSourceId(): String? {
        val stackIndex = getActivePspMediaSourceStackIndex() ?: return null
        return context.vsh.selectStack[stackIndex]
    }

    private fun getActiveOrSelectedPspMediaSourceId(): String? {
        return getActivePspMediaSourceId() ?: getSelectedPspMediaSourceId()
    }

    private fun getActivePspMediaSourceItem(): XmbItem? {
        val sourceId = getActiveOrSelectedPspMediaSourceId() ?: return null
        val category = context.vsh.categories.visibleItems.find { it.id == context.vsh.selectedCategoryId } ?: return null
        return category.content?.visibleItems?.find { it.id == sourceId }
    }

    private fun getActivePspMediaSourcePreviousItem(): XmbItem? {
        val sourceId = getActiveOrSelectedPspMediaSourceId() ?: return null
        val category = context.vsh.categories.visibleItems.find { it.id == context.vsh.selectedCategoryId } ?: return null
        val sourceItems = category.content?.visibleItems ?: return null
        val sourceIndex = sourceItems.indexOfFirst { it.id == sourceId }
        return if (sourceIndex > 0) sourceItems[sourceIndex - 1] else null
    }

    private fun pspStorageContentCenterY(psp: PspVirtualMetrics, selectedCenterY: Float, relativeIndex: Int, scrollOffset: Float): Float {
        if (relativeIndex == 0) return selectedCenterY + (scrollOffset * psp.s(46.0f))

        val direction = if (relativeIndex > 0) 1.0f else -1.0f
        val distance = abs(relativeIndex)
        val immediateGap = psp.s(56.0f)
        val compressedGap = psp.s(34.0f)
        val offset = immediateGap + ((distance - 1).coerceAtLeast(0) * compressedGap)
        return selectedCenterY + (direction * offset) + (scrollOffset * psp.s(46.0f))
    }

    private fun pspMediaSourceItemCenterY(psp: PspVirtualMetrics, sourceCenterY: Float, relativeIndex: Int, scrollOffset: Float): Float {
        return sourceCenterY + psp.s(48.0f) + ((relativeIndex + scrollOffset) * psp.s(32.0f))
    }

    private fun pspMediaContentItemCenterY(psp: PspVirtualMetrics, selectedCenterY: Float, relativeIndex: Int, scrollOffset: Float): Float {
        return selectedCenterY + ((relativeIndex + scrollOffset) * psp.s(40.0f))
    }

    private fun drawPspMediaContentSourceMarker(ctx: Canvas, item: XmbItem, psp: PspVirtualMetrics, centerY: Float) {
        val iconSize = psp.firstLevelUnselectedSize
        val centerX = scaling.viewport.left + psp.s(72.0f)
        verticalRectF.set(
            centerX - (iconSize.x * 0.5f),
            centerY - (iconSize.y * 0.5f),
            centerX + (iconSize.x * 0.5f),
            centerY + (iconSize.y * 0.5f)
        )
        iconPaint.alpha = 224
        if (item.hasIcon) {
            ctx.drawBitmap(item.icon, null, verticalRectF, iconPaint, FittingMode.FIT, 0.5f, 0.5f)
        }

        if (arrowBitmapLoaded) {
            iconPaint.alpha = 230
            val arrowHalf = psp.s(5.6f)
            tmpRectF.set(-arrowHalf, -arrowHalf, arrowHalf, arrowHalf)
            ctx.withTranslation(centerX + psp.s(43.0f), centerY) {
                ctx.drawBitmap(arrowBitmap, null, tmpRectF, iconPaint, FittingMode.FIT, 0.5f, 0.5f)
            }
        }
    }

    private fun drawPspMediaSourceRow(ctx: Canvas, item: XmbItem, psp: PspVirtualMetrics, centerX: Float, centerY: Float) {
        val width = psp.firstLevelSelectedSize.x
        val height = psp.firstLevelSelectedSize.y
        verticalRectF.set(centerX - (width * 0.5f), centerY - (height * 0.5f), centerX + (width * 0.5f), centerY + (height * 0.5f))

        iconPaint.alpha = 255
        if (item.hasIcon) {
            ctx.drawBitmap(item.icon, null, verticalRectF, iconPaint, FittingMode.FIT, 0.5f, 0.5f)
        }

        val textLeft = verticalRectF.right + psp.textGap
        val contentRight = psp.contentRight
        val dividerY = centerY - psp.memoryStickDividerOffsetY

        menuVerticalNamePaint.alpha = 255
        menuVerticalNamePaint.textSize = psp.rootNameSize
        menuVerticalDescPaint.alpha = 255
        menuVerticalDescPaint.textSize = psp.rootDescSize

        DrawExtension.scrollText(
            ctx,
            item.displayName,
            textLeft,
            contentRight,
            centerY,
            menuVerticalNamePaint,
            -0.48f,
            time.currentTime,
            5.0f
        )

        if (item.hasValue) {
            DrawExtension.scrollText(
                ctx,
                item.value,
                textLeft,
                contentRight,
                centerY,
                menuVerticalDescPaint,
                1.14f,
                time.currentTime,
                12.0f
            )
        }

        sortHeaderOutlinePaint.strokeWidth = 2.65f
        sortHeaderOutlinePaint.color = Color.argb(208, 255, 255, 255)
        ctx.drawLine(textLeft, dividerY, contentRight, dividerY, sortHeaderOutlinePaint)
    }

    private fun drawPspEmptyState(ctx: Canvas, title: String, detail: String, x: Float, y: Float, scale: Float) {
        menuVerticalNamePaint.apply {
            textSize = 11.7f * scale
            color = Color.argb(236, 255, 255, 255)
            textAlign = Paint.Align.LEFT
        }
        menuVerticalDescPaint.apply {
            textSize = 7.9f * scale
            color = Color.argb(226, 244, 246, 250)
            textAlign = Paint.Align.LEFT
        }

        ctx.drawText(title, x, y, menuVerticalNamePaint)
        ctx.drawText(detail, x, y + (12.8f * scale), menuVerticalDescPaint)
    }

    private fun updateAppOptionsHintState() {
        val hoveredItem = context.vsh.hoveredItem
        if (hoveredItem !is XmbAppItem || widgets.sideMenu.isDisplayed) {
            appOptionsHintItemId = ""
            appOptionsHintStartedAt = 0.0f
            return
        }

        if (hoveredItem.id != appOptionsHintItemId) {
            appOptionsHintItemId = hoveredItem.id
            appOptionsHintStartedAt = currentTime
        }
    }

    private fun drawAppOptionsHint(ctx: Canvas) {
        updateAppOptionsHintState()
        if (appOptionsHintItemId.isBlank()) return

        val elapsed = currentTime - appOptionsHintStartedAt
        val alphaFactor = when {
            elapsed < 0.5f -> 0.0f
            elapsed < 0.72f -> ((elapsed - 0.5f) / 0.22f).coerceIn(0.0f, 1.0f)
            elapsed < 3.25f -> 1.0f
            elapsed < 3.6f -> (1.0f - ((elapsed - 3.25f) / 0.35f)).coerceIn(0.0f, 1.0f)
            else -> 0.0f
        }

        if (alphaFactor <= 0.0f) return

        val isPSP = layoutMode == XmbLayoutType.PSP
        val psp = if (isPSP) pspMetrics() else null
        val margin = psp?.s(12.8f) ?: 26.0f
        val panelWidth = psp?.s(66.5f) ?: 146.0f
        val panelHeight = psp?.s(16.6f) ?: 38.0f
        val panelRight = scaling.viewport.right - margin
        val panelBottom = scaling.viewport.bottom - margin
        val panelLeft = panelRight - panelWidth
        val panelTop = panelBottom - panelHeight
        val alpha = (alphaFactor * 255.0f).roundToInt().coerceIn(0, 255)

        tmpRectF.set(panelLeft, panelTop, panelRight, panelBottom)
        appHintBgPaint.color = Color.argb((alphaFactor * 148.0f).roundToInt().coerceIn(0, 255), 58, 62, 68)
        ctx.drawRoundRect(tmpRectF, psp?.s(2.6f) ?: 6.0f, psp?.s(2.6f) ?: 6.0f, appHintBgPaint)

        val triLeft = panelLeft + (psp?.s(6.4f) ?: 15.0f)
        val hintTextBaseline = tmpRectF.centerY() + (psp?.s(1.6f) ?: 3.5f)
        val triChar = context.vsh.M.gamepadUi.getGamepadChar(PadKey.Triangle).toString()
        appHintButtonPaint.textSize = psp?.s(9.1f) ?: 18.0f
        appHintButtonPaint.color = Color.argb((alphaFactor * 238.0f).roundToInt().coerceIn(0, 255), 250, 252, 255)
        ctx.drawText(triChar, triLeft, hintTextBaseline, appHintButtonPaint, -0.5f)

        appHintTextPaint.textSize = psp?.s(7.6f) ?: 16.0f
        appHintTextPaint.color = Color.argb(alpha, 250, 252, 255)
        ctx.drawText("Options", panelLeft + (psp?.s(17.4f) ?: 37.0f), hintTextBaseline, appHintTextPaint, -0.5f)
    }
    
    private fun shouldPlayVideo(item:XmbItem, isSelected : Boolean) : Boolean {
        val byIcon = item.hasAnimatedIcon && item.isAnimatedIconLoaded
        if (!byIcon) return false
        return when(CifLoader.videoIconMode){
            VideoIconMode.AllTime -> true
            VideoIconMode.SelectedOnly -> {
                if (!isSelected) return false
                if (animatedIconSelectedItemId != item.id) {
                    animatedIconSelectedItemId = item.id
                    animatedIconSelectedStartedAt = currentTime
                    return false
                }
                currentTime - animatedIconSelectedStartedAt >= ANIMATED_ICON_SELECTED_DWELL
            }
            VideoIconMode.Disabled -> false
        }
    }

    private fun drawVerticalMenu(ctx:Canvas){
        val items = vsh.items?.copyList()?.visibleItems?.filterBySearch(context.vsh)

        val loadIcon = loadingIconBitmap
        val isPSP = layoutMode == XmbLayoutType.PSP
        val psp = if (isPSP) pspMetrics() else null
        val menuDispT = widgets.sideMenu.showMenuDisplayFactor
        val isLand = view.width > view.height
        val cursorY = context.vsh.itemCursorY
        val pspRowMode = getPspRowPresentationMode()
        val isStorageContentList = isPSP && pspRowMode == PspRowPresentationMode.StorageContentList
        val isMediaSourceList = isPSP && pspRowMode == PspRowPresentationMode.MediaSourceList
        val isMediaContentList = isPSP && pspRowMode == PspRowPresentationMode.MediaContentList
        val isPspRootNodeBrowsing = isPSP && context.vsh.isInRoot && cursorY > 0

        val hSeparation = psp?.itemSlideX ?: ps3IconSeparation.x
        val separation = psp?.itemSpacingY ?: ps3IconSeparation.y
        val center = ps3MenuIconCenter
        val pspColumnCenter = when {
            isMediaSourceList -> psp?.firstColumnCenter
            isMediaContentList -> psp?.secondColumnCenter
            context.vsh.isInRoot -> psp?.firstColumnCenter
            else -> psp?.secondColumnCenter
        }
        val xAnchor = pspColumnCenter?.x ?: (scaling.target.width() * center.x)
        val xPos = xAnchor + (context.vsh.itemOffsetX * hSeparation)
        val yPos = pspColumnCenter?.y ?: ((scaling.target.height() * center.y) + (separation * 2.0f))
        val contentRight = psp?.contentRight ?: (scaling.viewport.right - 20.0f)
        val valueLeft = psp?.valueLeft ?: (scaling.viewport.right - 400.0f)
        val iconCenterToText = (psp?.firstLevelSelectedSize?.x ?: ps3SelectedIconSize.x) * menuDispT.toLerp(0.60f, 0.75f)

        if (pspRowMode == PspRowPresentationMode.SettingsDetailList && psp != null) {
            drawPspSettingsDetailNodeContext(ctx, psp, yPos)
            val arrowX = psp.settingsDetailColumnCenter.x - psp.s(29.0f)
            ctx.withTranslation(arrowX, yPos) {
                ctx.withRotation(180f) {
                    val arrowHalf = psp.s(6.0f)
                    tmpRectF.set(-arrowHalf, -arrowHalf, arrowHalf, arrowHalf)
                    iconPaint.alpha = 255
                    ctx.drawBitmap(arrowBitmap, null, tmpRectF, iconPaint, FittingMode.FIT, 0.5f, 0.5f)
                }
            }
        } else if(!context.vsh.isInRoot && !isMediaSourceList && !isMediaContentList){
            val vsh = context.vsh
            val rootItem = vsh.categories.visibleItems.find { it.id == vsh.selectedCategoryId }
            var i = 0
            val iconSize = psp?.secondLevelUnselectedSize ?: ps3UnselectedIconSize
            val hSizeY = iconSize.y / 2.0f
            val hSizeX = iconSize.x / 2.0f

            iconPaint.alpha = 255

            val dxOffArr = if (isStorageContentList && psp != null) {
                scaling.viewport.left + psp.s(7.0f)
            } else {
                xPos - (psp?.breadcrumbArrowOffset ?: (ps3IconSeparation.x * 0.625f))
            }
            ctx.withTranslation(dxOffArr, yPos){
                ctx.withRotation(isStorageContentList.select(0f, 180f)){
                    val arrowHalf = psp?.s(6.0f) ?: 16.0f
                    tmpRectF.set(-arrowHalf, -arrowHalf, arrowHalf, arrowHalf)
                    ctx.drawBitmap(arrowBitmap, null, tmpRectF, iconPaint, FittingMode.FIT, 0.5f, 0.5f)
                }
            }

            iconPaint.alpha = 192

            if (!isStorageContentList) {
                try {
                    if (i < vsh.selectStack.size) {
                        var sItem = rootItem?.content?.find { it.id == vsh.selectStack[i] }
                        while (sItem != null && i < vsh.selectStack.size) {
                            val dxOff = xPos - ((psp?.breadcrumbStep ?: (ps3IconSeparation.x * 1.25f)) * (i + 1))
                            tmpRectF.set(dxOff - hSizeX, yPos - hSizeY, dxOff + hSizeX, yPos + hSizeY)
                            if (sItem.hasIcon) {
                                ctx.drawBitmap(
                                        sItem.icon,
                                        null,
                                        tmpRectF,
                                        iconPaint,
                                        FittingMode.FIT,
                                        0.5f
                                )
                            }
                            sItem = sItem.content?.find { it.id == vsh.selectStack[i] }
                            i++
                        }
                    }
                }catch(_:IndexOutOfBoundsException){}
            }
        }

        val mediaSourceCenterY = if (isMediaSourceList && psp != null) psp.firstColumnCenter.y else yPos
        if (isMediaSourceList && psp != null) {
            getActivePspMediaSourceItem()?.let { sourceItem ->
                drawPspMediaSourceRow(ctx, sourceItem, psp, xPos, mediaSourceCenterY)
            }
            return
        }
        if (isMediaContentList && psp != null) {
            getActivePspMediaSourceItem()?.let { sourceItem ->
                drawPspMediaContentSourceMarker(ctx, sourceItem, psp, yPos)
            }
        }

        if(items != null){
            if(items.isNotEmpty()) {
                for (wx in items.indices) {
                    val iy = wx - cursorY
                    val selected = iy == 0
                    val item = items[wx]

                    val textAlpha = when {
                        selected -> 255
                        isPSP && pspRowMode.isSettingsListMode() -> menuDispT.toLerp(136f, 72f).toInt()
                        else -> menuDispT.toLerp(128f, 0f).toInt()
                    }
                    menuVerticalDescPaint.alpha = textAlpha
                    menuVerticalNamePaint.alpha = textAlpha

                    val pspSelectedItemSize = if (context.vsh.isInRoot) {
                        psp?.firstLevelSelectedSize
                    } else if (pspRowMode == PspRowPresentationMode.SettingsDetailList) {
                        psp?.firstLevelUnselectedSize
                    } else if (pspRowMode == PspRowPresentationMode.StorageContentList) {
                        psp?.storageContentSelectedSize
                    } else if (pspRowMode == PspRowPresentationMode.MediaContentList) {
                        psp?.mediaContentItemSize
                    } else {
                        psp?.secondLevelSelectedSize
                    }
                    val pspUnselectedItemSize = if (context.vsh.isInRoot) {
                        psp?.firstLevelUnselectedSize
                    } else if (pspRowMode == PspRowPresentationMode.SettingsDetailList) {
                        psp?.firstLevelUnselectedSize
                    } else if (pspRowMode == PspRowPresentationMode.StorageContentList) {
                        psp?.storageContentUnselectedSize
                    } else if (pspRowMode == PspRowPresentationMode.MediaContentList) {
                        psp?.mediaContentItemSize
                    } else {
                        psp?.secondLevelUnselectedSize
                    }

                    val targetSize =
                            if (psp != null) {
                                selected.select(pspSelectedItemSize!!, pspUnselectedItemSize!!)
                            } else {
                                selected.select(ps3SelectedIconSize, ps3UnselectedIconSize)
                            }
                    var size = targetSize

                    if (selected) {
                        val sizeTransition = abs(context.vsh.itemOffsetY)
                        val previousSize =
                                if (psp != null) {
                                    selected.select(pspUnselectedItemSize!!, pspSelectedItemSize!!)
                                } else {
                                    selected.select(ps3UnselectedIconSize, ps3SelectedIconSize)
                                }
                        tmpPointFA.x = sizeTransition.toLerp(targetSize.x, previousSize.x)
                        tmpPointFA.y = sizeTransition.toLerp(targetSize.y, previousSize.y)
                        size = tmpPointFA
                    }

                    var hSizeX = size.x / 2.0f
                    var hSizeY = (size.y / 2.0f)
                    val rowCenterX = if (isStorageContentList && psp != null) {
                        val selectedLane = scaling.viewport.left + psp.s(86.0f)
                        val unselectedLane = scaling.viewport.left + psp.s(86.0f)
                        selected.select(selectedLane, unselectedLane) + (context.vsh.itemOffsetX * hSeparation)
                    } else if (isMediaContentList && psp != null) {
                        scaling.viewport.left + psp.s(154.0f) + (context.vsh.itemOffsetX * hSeparation)
                    } else if (pspRowMode == PspRowPresentationMode.SettingsDetailList && psp != null) {
                        psp.settingsDetailColumnCenter.x + (context.vsh.itemOffsetX * hSeparation)
                    } else {
                        xPos
                    }
                    var centerY = if (isStorageContentList && psp != null) {
                        pspStorageContentCenterY(psp, yPos, iy, context.vsh.itemOffsetY)
                    } else if (isMediaContentList && psp != null) {
                        pspMediaContentItemCenterY(psp, yPos, iy, context.vsh.itemOffsetY)
                    } else if (isMediaSourceList && psp != null) {
                        pspMediaSourceItemCenterY(psp, mediaSourceCenterY, iy, context.vsh.itemOffsetY)
                    } else {
                        yPos + ((iy + context.vsh.itemOffsetY) * separation)
                    }

                    iconPaint.alpha = 255
                    if(!selected){
                        hSizeX *= menuDispT.toLerp(1.0f, 0.75f)
                        hSizeY *= menuDispT.toLerp(1.0f, 0.75f)
                        iconPaint.alpha = if (isPSP && pspRowMode.isSettingsListMode()) {
                            menuDispT.toLerp(170f, 100f).toInt()
                        } else {
                            menuDispT.toLerp(255f, 128f).toInt()
                        }
                    }else{
                        val selectedScale = if (psp != null) 1.0f else 1.25f
                        hSizeX *= menuDispT.toLerp(1.0f, selectedScale)
                        hSizeY *= menuDispT.toLerp(1.0f, selectedScale)
                    }

                    if (isPspRootNodeBrowsing && iy < -1) {
                        item.screenVisibility = false
                        continue
                    }
                    if (isMediaSourceList && iy < 0) {
                        item.screenVisibility = false
                        continue
                    }
                    if (isPspRootNodeBrowsing && iy == -1) {
                        centerY = scaling.viewport.top + (hSizeY * 0.5f)
                    } else if (iy < 0 && !isMediaContentList) {
                        centerY -= size.y * context.vsh.isInRoot.select(3.0f, 0.5f)
                    }
                    if (!isStorageContentList && !isMediaSourceList && !isMediaContentList && iy > 0) centerY += size.y * 0.5f

                    val isInViewport =
                            (centerY + hSizeY) > scaling.viewport.top && (centerY - hSizeY) < scaling.viewport.bottom

                    item.screenVisibility = isInViewport

                    if (isInViewport) {
                        verticalRectF.set(rowCenterX - hSizeX, centerY - hSizeY, rowCenterX + hSizeX, centerY + hSizeY)
                        val isWideCardItem = isPSP && applyPspWideCardBounds(item, selected, hSizeY * 2.0f, rowCenterX, centerY)
                        if (item.hasIcon) {
                            if (selected) {
                                if (psp != null) {
                                    if (isWideCardItem) {
                                        drawSelectedIconGlow(ctx, verticalRectF, true)
                                    } else if (pspRowMode == PspRowPresentationMode.StorageContentList) {
                                        drawPspContentListGlow(ctx, verticalRectF)
                                    } else if (pspRowMode == PspRowPresentationMode.MediaContentList) {
                                        // PSP Photo/media content lists use a uniform thumbnail lane;
                                        // selection is carried by the pointer and metadata, not promotion glow.
                                    } else {
                                        val focusSize = when {
                                            context.vsh.isInRoot -> psp.firstLevelFocusSize
                                            pspRowMode == PspRowPresentationMode.SettingsDetailList -> psp.firstLevelFocusSize
                                            pspRowMode == PspRowPresentationMode.StorageContentList -> psp.storageContentFocusSize
                                            else -> psp.secondLevelFocusSize
                                        }
                                        tmpRectF.set(
                                            rowCenterX - (focusSize.x / 2.0f),
                                            centerY - (focusSize.y / 2.0f),
                                            rowCenterX + (focusSize.x / 2.0f),
                                            centerY + (focusSize.y / 2.0f)
                                        )
                                        drawSelectedIconGlow(ctx, tmpRectF, true)
                                    }
                                } else {
                                    drawSelectedIconGlow(ctx, verticalRectF, false)
                                }
                            }
                            val iconAnchorX = (isPSP).select(0.5f, 0.5f)
                            if (shouldPlayVideo(item, selected)) {
                                val animIconBm = item.animatedIcon.getFrame(time.deltaTime)
                                ctx.drawBitmap(animIconBm, null, verticalRectF, iconPaint, FittingMode.FIT, iconAnchorX, 0.5f)
                            } else {
                                // ctx.drawRect(verticalRectF, menuVerticalDescPaint)
                                if (item.isIconLoaded) {
                                    ctx.drawBitmap(item.icon, null, verticalRectF, iconPaint, FittingMode.FIT, iconAnchorX, 0.5f)
                                } else {
                                    if (loadIcon != null) {
                                        ctx.withRotation(
                                                ((time.currentTime + (wx * 0.375f)) * -360.0f) % 360.0f, verticalRectF.centerX(), verticalRectF.centerY()) {
                                            ctx.drawBitmap(loadIcon, null, verticalRectF, iconPaint, FittingMode.FIT, iconAnchorX, 0.5f)
                                        }
                                    }
                                }
                            }
                        }

                        if (selected) {
                            if (item.hasBackSound && item.isBackSoundLoaded) M.audio.setAudioSource(item.backSound)
                            else M.audio.removeAudioSource()
                        }

                        val shouldDrawRowText = !isPSP || selected || pspRowMode.isSettingsListMode()
                        if (shouldDrawRowText) {
                            val textLeft = if (isPSP) {
                                verticalRectF.right + (psp?.textGap ?: 14.0f)
                            } else {
                                verticalRectF.centerX() + iconCenterToText
                            }

                            if (isPSP) {
                                val isFirstLevel = context.vsh.isInRoot
                                menuVerticalNamePaint.textSize = when {
                                    isFirstLevel -> psp?.rootNameSize ?: 35.0f
                                    pspRowMode == PspRowPresentationMode.StorageContentList -> psp?.storageContentNameSize ?: 30.0f
                                    else -> psp?.nestedNameSize ?: 28.0f
                                }
                                menuVerticalDescPaint.textSize = when {
                                    isFirstLevel -> psp?.rootDescSize ?: 25.0f
                                    pspRowMode == PspRowPresentationMode.StorageContentList -> psp?.storageContentDescSize ?: 21.0f
                                    else -> psp?.nestedDescSize ?: 20.0f
                                }
                            } else {
                                menuVerticalNamePaint.textSize = 25.0f
                                menuVerticalDescPaint.textSize = 15.0f
                            }

                            val isMemoryStickRow = isPSP && item.id in PSP_MEMORY_STICK_ROW_IDS
                            var dispNameYOffset = 0.5f

                            var dNameEnd = contentRight
                            var shouldDrawMemoryStickDivider = false
                            var memoryStickDividerY = centerY + 10.0f
                            var memoryStickDividerEndX = contentRight
                            val shouldDrawSecondaryText =
                                !isPSP || !pspRowMode.isSettingsListMode() || selected

                            if(isLand){
                                if(shouldDrawSecondaryText && item.hasDescription && !isMemoryStickRow){
                                    //ctx.drawText(item.description, textLeft, centerY, menuVerticalDescPaint, 1.1f)
                                    DrawExtension.scrollText(
                                            ctx,
                                            item.description,
                                            textLeft,
                                            contentRight,
                                            centerY, menuVerticalDescPaint,
                                            1.1f,
                                            time.currentTime,
                                            24.0f
                                    )
                                    dispNameYOffset= -0.25f
                                }
                                if(shouldDrawSecondaryText && item.hasValue && !widgets.sideMenu.isDisplayed){
                                    if (isMemoryStickRow) {
                                        dispNameYOffset = -0.48f
                                        DrawExtension.scrollText(
                                            ctx,
                                            item.value,
                                            textLeft,
                                            contentRight,
                                            centerY, menuVerticalDescPaint,
                                            1.14f,
                                            time.currentTime,
                                            12.0f
                                        )
                                        shouldDrawMemoryStickDivider = true
                                        memoryStickDividerY = centerY - (psp?.memoryStickDividerOffsetY ?: 4.1f)
                                        memoryStickDividerEndX = contentRight
                                    } else {
                                        DrawExtension.scrollText(
                                                ctx,
                                                item.value,
                                                valueLeft,
                                                contentRight,
                                                centerY, menuVerticalDescPaint,
                                                -0.25f,
                                                time.currentTime,
                                                24.0f
                                        )
                                        dNameEnd = valueLeft - (psp?.s(10.0f) ?: 50.0f)
                                        //ctx.drawText(item.value, valueLeft, centerY, menuVerticalDescPaint, -0.25f)
                                    }
                                }
                            }else{

                                dNameEnd = contentRight
                                var itemDesc = ""
                                var hasBottomText = false
                                if(shouldDrawSecondaryText && item.hasValue){
                                    itemDesc = item.value
                                    hasBottomText = itemDesc.isNotBlank()
                                }else if(shouldDrawSecondaryText && item.hasDescription){
                                    itemDesc = item.description
                                    hasBottomText = itemDesc.isNotBlank()
                                }

                                if(hasBottomText){
                                    dispNameYOffset= isMemoryStickRow.select(-0.48f, -0.25f)
                                    DrawExtension.scrollText(
                                            ctx,
                                            itemDesc,
                                            textLeft,
                                            contentRight,
                                            centerY, menuVerticalDescPaint,
                                            isMemoryStickRow.select(1.14f, 1.1f),
                                            time.currentTime,
                                            5.0f
                                    )
                                    //ctx.drawText(itemDesc, textLeft, centerY, menuVerticalDescPaint, 1.1f)
                                    if (isMemoryStickRow) {
                                        shouldDrawMemoryStickDivider = true
                                        memoryStickDividerY = centerY - (psp?.memoryStickDividerOffsetY ?: 4.1f)
                                        memoryStickDividerEndX = contentRight
                                    }
                                }
                            }
                            DrawExtension.scrollText(
                                    ctx,
                                    item.displayName,
                                    textLeft,
                                    dNameEnd,
                                    centerY, menuVerticalNamePaint,
                                    dispNameYOffset,
                                    time.currentTime,
                                    5.0f
                            )
                            //ctx.drawText(item.displayName, textLeft, centerY, menuVerticalNamePaint, dispNameYOffset)
                            if (shouldDrawMemoryStickDivider) {
                                sortHeaderOutlinePaint.strokeWidth = 2.65f
                                sortHeaderOutlinePaint.color = Color.argb(
                                    selected.select(208, menuDispT.toLerp(188f, 108f).toInt()),
                                    255,
                                    255,
                                    255
                                )
                                ctx.drawLine(textLeft, memoryStickDividerY, memoryStickDividerEndX, memoryStickDividerY, sortHeaderOutlinePaint)
                            }
                        }
                    }
                }
            } else {
                val vsh = context.vsh
                val isPspEmptyMediaView = isPSP && (
                    isMediaSourceList ||
                        vsh.selectedCategoryId == Vsh.ITEM_CATEGORY_VIDEO ||
                        vsh.selectedCategoryId == Vsh.ITEM_CATEGORY_PHOTO
                    )

                if (isPspEmptyMediaView) {
                    val title = when (vsh.selectedCategoryId) {
                        Vsh.ITEM_CATEGORY_MUSIC -> context.getString(R.string.empty_music)
                        Vsh.ITEM_CATEGORY_VIDEO -> context.getString(R.string.empty_video)
                        else -> context.getString(R.string.empty_photo)
                    }
                    val detail = when (vsh.selectedCategoryId) {
                        Vsh.ITEM_CATEGORY_MUSIC -> context.getString(R.string.empty_music_hint)
                        Vsh.ITEM_CATEGORY_VIDEO -> context.getString(R.string.empty_video_hint)
                        else -> context.getString(R.string.empty_photo_hint)
                    }
                    val textLeft = xPos + ((psp?.firstLevelSelectedSize?.x ?: 0.0f) / 2.0f) + (psp?.emptyStateTextGap ?: 54.0f)
                    val textTop = if (isMediaSourceList && psp != null) {
                        pspMediaSourceItemCenterY(psp, mediaSourceCenterY, 0, 0.0f) - psp.s(4.5f)
                    } else {
                        yPos - (psp?.s(4.5f) ?: 12.0f)
                    }
                    drawPspEmptyState(ctx, title, detail, textLeft, textTop, psp?.scale ?: 1.0f)
                } else {
                    val eString = when(vsh.selectedCategoryId){
                        Vsh.ITEM_CATEGORY_MUSIC -> context.getString(R.string.empty_music)
                        Vsh.ITEM_CATEGORY_VIDEO -> context.getString(R.string.empty_video)
                        Vsh.ITEM_CATEGORY_PHOTO -> context.getString(R.string.empty_photo)
                        Vsh.ITEM_CATEGORY_GAME -> context.getString(R.string.empty_game)
                        Vsh.ITEM_CATEGORY_PSN -> context.getString(R.string.empty_psn)
                        else -> context.getString(R.string.category_is_empty)
                    }
                    val xxPos = xPos + (ps3SelectedIconSize.x / 2.0f)
                    menuVerticalNamePaint.color = Color.WHITE
                    ctx.drawText(eString, xxPos, yPos, menuVerticalNamePaint)
                }
                M.audio.removeAudioSource()
            }
        }
    }

    private val touchTestRectF = RectF()
    private val touchTestSearchRectF = RectF()

    override fun end() {
    }

    private fun openSearchQuery(){
        val item = vsh.activeParent
        if(item != null){
            val q = item.getProperty(Consts.XMB_ACTIVE_SEARCH_QUERY, "")
            NativeEditTextDialog(context.vsh)
                    .setOnFinish {  item.setProperty(Consts.XMB_ACTIVE_SEARCH_QUERY, it) }
                    .setTitle(context.getString(R.string.dlg_set_search_query))
                    .setValue(q)
                    .show()
        }
    }

    override fun onGamepadInput(key: PadKey, isDown: Boolean) : Boolean {
        var retval = false
        val vsh = context.vsh

        if(isDown){
            when(key){
                PadKey.PadL -> {
                    if(vsh.isInRoot){
                        vsh.moveCursorX(false)
                    }else{
                        vsh.backStep()
                    }
                    retval = true
                }
                PadKey.PadR -> {
                    if(vsh.isInRoot){
                        context.vsh.moveCursorX(true)
                        retval = true
                    }
                }
                PadKey.PadU -> {
                    context.vsh.moveCursorY(false)
                    retval = true
                }
                PadKey.PadD -> {

                    context.vsh.moveCursorY(true)
                    retval = true
                }
                PadKey.Triangle -> {
                    val item = vsh.hoveredItem
                    if(item != null){
                        if(item.hasMenu){
                            view.showSideMenu(true)
                        }
                    }
                    retval = true
                }
                PadKey.Select -> {
                    openSearchQuery()
                }
                PadKey.Square -> {
                    if(vsh.isInRoot){
                        vsh.doCategorySorting()
                        sortHeaderDisplay = 5.0f
                        retval = true
                    }
                }
                PadKey.Confirm, PadKey.StaticConfirm -> {
                    // Start Timer
                    if(isOpenMenuOnHold){
                        vsh.mainHandle.postDelayed(::openMenuOnHoldAction, 500L)
                        isOpenMenuHeld = true
                        widgets.sideMenu.disableMenuExec = true
                    }else{
                        vsh.launchActiveItem()
                    }
                    retval = true
                }
                PadKey.Cancel, PadKey.StaticCancel -> {
                    vsh.backStep()
                    retval = true
                }
                else -> {  }
            }
        }else{
            when(key){
                PadKey.Confirm, PadKey.StaticConfirm -> {
                    if(isOpenMenuOnHold){
                        if(isOpenMenuHeld){
                            vsh.mainHandle.removeCallbacks(::openMenuOnHoldAction)
                            vsh.launchActiveItem()
                            isOpenMenuHeld = false
                            retval = true
                        } else {
                            retval = false
                        }
                        widgets.sideMenu.disableMenuExec = false
                    }else{
                        retval = false
                    }
                }
                else -> {}
            }
        }
        return retval
    }

    private fun openMenuOnHoldAction(){
        val vsh = context.vsh
        val item = vsh.hoveredItem

        if(!isOpenMenuHeld) return

        if(item != null){
            if(item.hasMenu){
                view.showSideMenu(true)
            }
        }
        isOpenMenuHeld = false
    }

    override fun onTouchScreen(start: PointF, current: PointF, action: Int) {

        val isPSP = layoutMode == XmbLayoutType.PSP
        val psp = if (isPSP) pspMetrics() else null

        when (action) {
            MotionEvent.ACTION_UP -> {
                if(directionLock == DirectionLock.None){
                    val iconSize = if (psp != null) {
                        context.vsh.isInRoot.select(psp.firstLevelFocusSize, psp.secondLevelFocusSize)
                    } else {
                        ps3SelectedIconSize
                    }
                    val hSeparation = psp?.itemSlideX ?: ps3IconSeparation.x
                    val separation = psp?.itemSpacingY ?: ps3IconSeparation.y
                    val center = ps3MenuIconCenter
                    val pspColumnCenter = if (context.vsh.isInRoot) psp?.firstColumnCenter else psp?.secondColumnCenter
                    val xPos =
                        (pspColumnCenter?.x ?: (scaling.target.width() * center.x)) + (context.vsh.itemOffsetX * hSeparation)
                    val yPos = pspColumnCenter?.y ?: ((scaling.target.height() * center.y) + (separation * 2.0f))
                    val hSizeX = iconSize.x * 0.5f
                    val hSizeY = iconSize.y * 0.5f
                    touchTestRectF.set(
                        xPos - hSizeX,
                        yPos - hSizeY,
                        xPos + hSizeX,
                        yPos + hSizeY
                    )

                    if(context.vsh.isInRoot){
                        touchTestSearchRectF.set(
                            xPos - hSizeX,
                            yPos - hSizeY - iconSize.y,
                            xPos + hSizeX,
                            yPos - hSizeY,
                        )
                    }else{
                        touchTestSearchRectF.set(
                            xPos - hSizeX - iconSize.x,
                            yPos - hSizeY - iconSize.y,
                            xPos - hSizeX,
                            yPos - hSizeY,
                        )

                    }

                    if (touchTestRectF.contains(start)) {
                        context.vsh.launchActiveItem()
                    }else if(touchTestSearchRectF.contains(start)){
                        openSearchQuery()
                    }
                    else if(current.x < 200.0f){
                        context.vsh.backStep()
                    }
                }
                directionLock = DirectionLock.None
            }
            MotionEvent.ACTION_MOVE -> {
                val isMenu = start.x > scaling.target.right - 200.0f
                if(isMenu){
                    val menuTol = (view.width > view.height).select(400.0f, 100.0f)
                    if(current.x <  scaling.target.right - menuTol){
                        val item = context.vsh.hoveredItem
                        if(item?.hasMenu == true){
                            view.showSideMenu(true)
                        }
                    }
                }else {
                    val iconSize = if (psp != null) {
                        context.vsh.isInRoot.select(psp.firstLevelUnselectedSize, psp.secondLevelUnselectedSize)
                    } else {
                        ps3UnselectedIconSize
                    }
                    if (directionLock != DirectionLock.Horizontal) { // Vertical
                        val yLen = current.y - start.y
                        if (abs(yLen) > iconSize.y) {
                            context.vsh.moveCursorY(yLen < 0.0f)
                            context.xmb.touchStartPointF.set(current)
                            directionLock = DirectionLock.Vertical
                        }
                    }

                    if (directionLock != DirectionLock.Vertical) { // Horizontal
                        val xLen = current.x - start.x
                        if (abs(xLen) > iconSize.x) {
                            if (context.vsh.isInRoot) {
                                context.vsh.moveCursorX(xLen < 0.0f)
                            } else {
                                if (xLen > 0.0f) {
                                    context.vsh.backStep()
                                }
                            }
                            context.xmb.touchStartPointF.set(current)
                            directionLock = DirectionLock.Horizontal
                        }
                    }
                }
            }
        }
    }

    private fun updateColdBootWaveAnimation(){
        val speed = M.pref.get(XMBWaveSurfaceView.KEY_SPEED, 1.0f)
        if(coldBootTransition > 1.0f) {
            val firstHalfTransition = coldBootTransition - 1.0f;
            NativeGL.setSpeed(firstHalfTransition.toLerp(25.0f, 0.5f))
            NativeGL.setVerticalScale(firstHalfTransition.toLerp(1.25f, coldBootWaveVerticalScale))
            coldBootTransition -= time.deltaTime * 4.0f
        }else{
            NativeGL.setSpeed(coldBootTransition.toLerp(speed, 25.0f))
            NativeGL.setVerticalScale(coldBootTransition.toLerp(1.0f, 1.25f))
            coldBootTransition -= time.deltaTime * 2.0f
        }

        if(coldBootTransition < 0.0f){
            context.vsh.waveShouldReReadPreferences = true
            NativeGL.setVerticalScale( 1.0f )
            NativeGL.setSpeed( 1.0f )
            context.xmb.waveView?.refreshNativePreferences(forceStyleNudge = true)
            coldBootTransition = -2.0f;
        }
    }

    private fun drawSortHeaderDisplay(ctx:Canvas){
        if(sortHeaderDisplay > 0.0f){
            val selCat = vsh.categories.visibleItems.find {it.id == vsh.selectedCategoryId}
            if(selCat is XmbItemCategory){
                if(selCat.sortable){
                    val t = (
                            if(sortHeaderDisplay > 3.0f) sortHeaderDisplay.lerpFactor(5.0f, 4.75f)
                            else sortHeaderDisplay.lerpFactor(0.0f, 0.25f)
                            ).coerceIn(0.0f, 1.0f)
                    val textName = selCat.sortModeName
                    val scale = t.toLerp(2.0f, 1.0f)
                    ctx.withScale(scale, scale,  scaling.target.width() / 2.0f, scaling.target.height() / 2.0f){
                        sortHeaderFillPaint.alpha = (t * t).toLerp(0.0f,128.0f).toInt()
                        sortHeaderOutlinePaint.alpha = (t * t).toLerp(0.0f,255.0f).toInt()
                        sortHeaderTextPaint.alpha = (t * t).toLerp(0.0f,255.0f).toInt()
                        sortHeaderTextPaint.textAlign = Paint.Align.LEFT

                        tmpRectF.set(scaling.viewport.left - 10.0f, scaling.viewport.top - 10.0f,
                                scaling.viewport.right + 10.0f, 150.0f)
                        ctx.drawRect(tmpRectF, sortHeaderFillPaint)
                        ctx.drawRect(tmpRectF, sortHeaderOutlinePaint)

                        ctx.drawText(textName, 75f, tmpRectF.bottom - 50.0f, sortHeaderTextPaint, 0.5f)
                    }
                }
            }
            sortHeaderDisplay -= time.deltaTime
        }
    }

    override fun render(ctx: Canvas) {
        currentTime += time.deltaTime
        menuScaleTime = (time.deltaTime * 10.0f).toLerp(menuScaleTime, 0.0f).coerceIn(0f,1f)
        val menuScale = menuScaleTime.toLerp(1.0f, 2.0f).coerceIn(1.0f, 2.0f)

        if(coldBootTransition > -1.0f){
            updateColdBootWaveAnimation()
        }

        try {
            drawBackground(ctx)
        }catch(_:ConcurrentModificationException){}
        ctx.withScale(menuScale, menuScale, scaling.target.centerX(), scaling.target.centerY()){
            try{
                drawVerticalMenu(ctx)
                drawHorizontalMenu(ctx)
                widgets.searchQuery.render(ctx)
                widgets.statusBar.render(ctx)
                drawAppOptionsHint(ctx)
                drawStorageNodeSpinner(ctx)
                drawSortHeaderDisplay(ctx)
            }catch(_:ConcurrentModificationException){}
        }
    }
}
