package id.psw.vshlauncher.views.dialogviews

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.text.TextPaint
import android.view.MotionEvent
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.withRotation
import id.psw.vshlauncher.FittingMode
import id.psw.vshlauncher.R
import id.psw.vshlauncher.Vsh
import id.psw.vshlauncher.postNotification
import id.psw.vshlauncher.select
import id.psw.vshlauncher.toLerp
import id.psw.vshlauncher.xmb
import id.psw.vshlauncher.submodules.PadKey
import id.psw.vshlauncher.types.XmbItem
import id.psw.vshlauncher.types.items.XmbCustomLaunchItem
import id.psw.vshlauncher.typography.FontCollections
import id.psw.vshlauncher.views.XmbDialogSubview
import id.psw.vshlauncher.views.XmbView
import id.psw.vshlauncher.views.drawBitmap
import kotlin.math.abs

class CustomLaunchItemInfoDialogView(
    v: XmbView,
    private val item: XmbCustomLaunchItem
) : XmbDialogSubview(v) {
    companion object {
        private const val POS_CUSTOM_ICON = 0
        private const val POS_CUSTOM_ANIM_ICON = 1
        private const val POS_CUSTOM_BACKDROP = 2
        private const val POS_CUSTOM_BACKDROP_OVERLAY = 3
        private const val POS_GAMEBOOT = 4
        private const val POS_RESET_ICON = 5
        private const val POS_RESET_BACKDROP = 6
        private const val TRANSITE_TIME = 0.125f

        private val bmpRectF = RectF()
        private val selRectF = RectF()
        private val validSelections = intArrayOf(
            POS_CUSTOM_ICON,
            POS_CUSTOM_ANIM_ICON,
            POS_CUSTOM_BACKDROP,
            POS_CUSTOM_BACKDROP_OVERLAY,
            POS_GAMEBOOT,
            POS_RESET_ICON,
            POS_RESET_BACKDROP
        )
        private val selectableRowIndices = intArrayOf(3, 4, 5, 6, 7, 8, 9)
    }

    override val hasNegativeButton: Boolean = true
    override val hasPositiveButton: Boolean = true
    override val title: String get() = vsh.getString(R.string.view_app_info)
    override val negativeButton: String get() = vsh.getString(R.string.common_back)
    override val positiveButton: String get() = vsh.getString(R.string.common_change)

    private var cursorPos = 0
    private var transiteTime = 0.0f
    private lateinit var loadIcon: Bitmap
    private val tPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 20.0f
        typeface = FontCollections.masterFont
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2.0f
    }
    private val drawBound = RectF()
    private var touchHasMove = false

    override val icon: Bitmap
        get() = vsh.loadTexture(R.drawable.icon_info, "dialog_icon_custom_launch_info", true)

    override fun onStart() {
        loadIcon = ResourcesCompat.getDrawable(vsh.resources, R.drawable.ic_sync_loading, null)
            ?.toBitmap(256, 256) ?: XmbItem.WHITE_BITMAP
        super.onStart()
    }

    override fun onClose() {
        if (loadIcon != XmbItem.WHITE_BITMAP) loadIcon.recycle()
    }

    private fun drawLoading(ctx: Canvas) {
        val time = vsh.xmbView?.time?.currentTime ?: (System.currentTimeMillis() / 1000.0f)
        ctx.withRotation(
            ((time + 0.375f) * -360.0f) % 360.0f,
            bmpRectF.centerX(),
            bmpRectF.centerY()
        ) {
            ctx.drawBitmap(loadIcon, null, bmpRectF, iconPaint, FittingMode.FIT, 0.5f, 0.5f)
        }
    }

    override fun onDraw(ctx: Canvas, drawBound: RectF, deltaTime: Float) {
        this.drawBound.set(drawBound)

        if (transiteTime < TRANSITE_TIME) {
            transiteTime += vsh.xmbView?.time?.deltaTime ?: 0.015f
        }
        transiteTime = transiteTime.coerceIn(0.0f, TRANSITE_TIME)

        val t = transiteTime / TRANSITE_TIME
        val cSizeY = 132.0f
        val sizeX = t.toLerp(320.0f, 240.0f)
        val sizeY = t.toLerp(176.0f, cSizeY)
        val hSizeX = sizeX / 2.0f
        val hSizeY = sizeY / 2.0f
        val iconCX = t.toLerp((0.3f).toLerp(drawBound.left, drawBound.right), drawBound.centerX())
        val iconCY = t.toLerp(drawBound.centerY(), drawBound.top + 20.0f + hSizeY)

        bmpRectF.set(iconCX - hSizeX, iconCY - hSizeY, iconCX + hSizeX, iconCY + hSizeY)

        if (item.hasAnimatedIcon) {
            if (item.isAnimatedIconLoaded) {
                val tt = vsh.xmbView?.time?.deltaTime ?: 0.015f
                ctx.drawBitmap(item.animatedIcon.getFrame(tt), null, bmpRectF, iconPaint, FittingMode.FIT, 0.5f, 0.5f)
            } else {
                drawLoading(ctx)
            }
        } else if (item.hasIcon) {
            if (item.isIconLoaded) {
                ctx.drawBitmap(item.icon, null, bmpRectF, iconPaint, FittingMode.FIT, 0.5f, 0.5f)
            } else {
                drawLoading(ctx)
            }
        }

        var sY = drawBound.top + cSizeY + 50.0f
        val cX = drawBound.centerX() - 100.0f
        val rows = listOf(
            R.string.dlg_info_name to item.displayName,
            R.string.dlg_info_desc to item.platformId.ifBlank { "-" },
            R.string.dlg_info_pkg_name to item.targetSummary.ifBlank { "-" },
            R.string.dlg_info_custom_icon to item.customIconPath.ifEmpty { "-" },
            R.string.dlg_info_custom_anim_icon to item.customAnimIconPath.ifEmpty { "-" },
            R.string.dlg_info_custom_backdrop to item.customBackdropPath.ifEmpty { "-" },
            R.string.dlg_info_custom_backdrop_overlay to item.customBackdropOverlayPath.ifEmpty { "-" },
            R.string.app_enable_gameboot to vsh.getString(item.gamebootEnabled.select(R.string.common_yes, R.string.common_no)),
            R.string.dlg_info_reset_custom_icon to "",
            R.string.dlg_info_reset_custom_backdrop to ""
        )

        rows.forEachIndexed { index, (label, value) ->
            tPaint.textAlign = Paint.Align.RIGHT
            ctx.drawText(vsh.getString(label), cX, sY, tPaint)

            val selectionIndex = selectableRowIndices[cursorPos.coerceIn(0, selectableRowIndices.size - 1)]
            if (selectionIndex == index) {
                val width = tPaint.measureText(value)
                selRectF.set(cX + 20.0f, sY - tPaint.textSize, cX + 50.0f + width, sY + 5.0f)
                ctx.drawRoundRect(selRectF, 5.0f, 5.0f, rectPaint)
            }

            tPaint.textAlign = Paint.Align.LEFT
            ctx.drawText(value, cX + 30.0f, sY, tPaint)
            sY += tPaint.textSize * 1.2f
        }
    }

    override fun onGamepad(key: PadKey, isPress: Boolean): Boolean {
        when (key) {
            PadKey.PadU -> if (isPress) {
                cursorPos = (cursorPos - 1).coerceIn(0, validSelections.size - 1)
                return true
            }
            PadKey.PadD -> if (isPress) {
                cursorPos = (cursorPos + 1).coerceIn(0, validSelections.size - 1)
                return true
            }
            else -> {}
        }
        return super.onGamepad(key, isPress)
    }

    override fun onTouch(a: PointF, b: PointF, act: Int) {
        if (act == MotionEvent.ACTION_MOVE) {
            val diff = a.y - b.y
            if (abs(diff) > 50.0f) {
                cursorPos = (cursorPos + if (diff > 0.0f) -1 else 1).coerceIn(0, validSelections.size - 1)
                b.y += 100.0f
                touchHasMove = true
                vsh.xmbView!!.context.xmb.touchStartPointF.set(b)
            }
        } else if (act == MotionEvent.ACTION_UP) {
            if (!touchHasMove) {
                if (a.y > drawBound.height() * 0.6f) cursorPos++
                else if (a.y < drawBound.height() * 0.3f) cursorPos--
                cursorPos = cursorPos.coerceIn(0, validSelections.size - 1)
            }
            touchHasMove = false
        } else if (act == MotionEvent.ACTION_DOWN) {
            touchHasMove = false
        }
        super.onTouch(a, b, act)
    }

    override fun onDialogButton(isPositive: Boolean) {
        if (!isPositive) {
            finish(view.screens.mainMenu)
            return
        }

        when (validSelections[cursorPos]) {
            POS_CUSTOM_ICON -> startPicker(Vsh.ACT_REQ_PICK_CUSTOM_ICON, "image/*")
            POS_CUSTOM_ANIM_ICON -> startPicker(
                Vsh.ACT_REQ_PICK_CUSTOM_ANIM_ICON,
                "*/*",
                arrayOf("image/webp", "image/apng", "image/gif")
            )
            POS_CUSTOM_BACKDROP -> startPicker(Vsh.ACT_REQ_PICK_CUSTOM_BACKDROP, "image/*")
            POS_CUSTOM_BACKDROP_OVERLAY -> startPicker(Vsh.ACT_REQ_PICK_CUSTOM_BACKDROP_OVERLAY, "image/*")
            POS_GAMEBOOT -> toggleGameboot()
            POS_RESET_ICON -> {
                item.resetCustomIcon()
                vsh.postNotification(R.drawable.category_setting, item.displayName, vsh.getString(R.string.common_success))
            }
            POS_RESET_BACKDROP -> {
                item.resetCustomBackdrop()
                vsh.postNotification(R.drawable.category_setting, item.displayName, vsh.getString(R.string.common_success))
            }
        }
    }

    private fun startPicker(requestCode: Int, mimeType: String, extraMimeTypes: Array<String>? = null) {
        vsh.itemEditing = item
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimeType
            if (extraMimeTypes != null) putExtra(Intent.EXTRA_MIME_TYPES, extraMimeTypes)
        }
        vsh.xmbView?.context?.xmb?.startActivityForResult(intent, requestCode)
    }

    private fun toggleGameboot() {
        vsh.toggleCustomLaunchItemGameboot(item.id)
        vsh.postNotification(R.drawable.category_setting, item.displayName, vsh.getString(R.string.common_success))
    }
}
