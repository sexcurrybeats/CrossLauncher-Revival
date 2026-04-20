package id.psw.vshlauncher.views.widgets

import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.text.format.DateFormat
import id.psw.vshlauncher.FColor
import id.psw.vshlauncher.VshBaseDirs
import id.psw.vshlauncher.VshResName
import id.psw.vshlauncher.VshResTypes
import id.psw.vshlauncher.getBatteryLevel
import id.psw.vshlauncher.isBatteryCharging
import id.psw.vshlauncher.makeTextPaint
import id.psw.vshlauncher.select
import id.psw.vshlauncher.submodules.BitmapRef
import id.psw.vshlauncher.typography.parseEncapsulatedBracket
import id.psw.vshlauncher.types.FileQuery
import id.psw.vshlauncher.views.XmbLayoutType
import id.psw.vshlauncher.views.XmbView
import id.psw.vshlauncher.views.XmbWidget
import id.psw.vshlauncher.views.drawRoundRect
import id.psw.vshlauncher.views.drawText
import id.psw.vshlauncher.views.setColorAndSize
import id.psw.vshlauncher.vsh
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class XmbStatusBar(view: XmbView) : XmbWidget(view) {
    companion object {
        private const val LEGACY_DEFAULT_FORMAT = "{operator} {sdf:dd/M HH:mm a}"
        private const val COMPACT_DEFAULT_FORMAT = "{datetime}"
        private const val BATTERY_GLYPH_DIR = "hud"
    }

    var disabled = false

    private val statusFillPaint : Paint = vsh.makeTextPaint(color = FColor.setAlpha(Color.WHITE, 0.5f)).apply {
        style = Paint.Style.FILL
        strokeWidth = 3.0f
    }
    private val statusTextPaint : Paint = vsh.makeTextPaint(size = 10.0f, color = Color.WHITE).apply {
        style = Paint.Style.FILL
        strokeWidth = 3.0f
    }
    private val statusOutlinePaint : Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.0f
        color = Color.WHITE
    }
    private val statusShadowFillPaint : Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(96, 0, 0, 0)
    }
    private val statusShadowOutlinePaint : Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.0f
        color = Color.argb(68, 0, 0, 0)
    }
    private val defaultBatteryBackdropPaint : Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(88, 96, 96, 100)
    }
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val batteryGlyphShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = PorterDuffColorFilter(Color.argb(86, 0, 0, 0), PorterDuff.Mode.SRC_IN)
    }

    private var baseDefRect = RectF()
    private var tmpRectF = RectF()
    private val tmpPath = Path()
    private val whitespaceRegex = Regex("\\s+")
    var showAnalogClock = true
    var pspPadStatusBar = true
    var dateTimeFormat = COMPACT_DEFAULT_FORMAT
    var pspShowBattery = true
    var showMobileOperator = false
    private var cachedStatusTextAtSecond = Long.MIN_VALUE
    private var cachedStatusTextKey = ""
    private var cachedStatusText = ""
    private var batteryGlyphRef: BitmapRef? = null
    private var batteryGlyphPath: String? = null
    private var batteryGlyphResolved = false

    /**
     * Encapsulated text format
     * Values:
     * - `operator` - Network Name
     * - `sdf:(sdf_format)` - Simple Date Format, for reference see [SimpleDateFormat](https://docs.oracle.com/javase/7/docs/api/java/text/SimpleDateFormat.html)
     */
    fun format(src:String) : String {
        val sb = StringBuilder()
        val cl = Calendar.getInstance()
        src.parseEncapsulatedBracket().forEachIndexed { i, s ->
            if(i % 2 == 0){
                sb.append(s)
            }else{
                sb.append(when(s){
                    "operator" -> {
                        if(showMobileOperator) M.network.operatorName else ""
                    }
                    "battery" -> {
                        (vsh.getBatteryLevel() * 100).toInt().toString()
                    }
                    "charging" -> {
                        if (vsh.isBatteryCharging()) "+" else ""
                    }
                    "datetime" -> {
                        getCompactDateTimeText(cl)
                    }
                    else -> {
                        when {
                            s.startsWith("sdf:") -> {
                                SimpleDateFormat(s.substring(4), Locale.getDefault()).format(cl.time)
                            }
                            s.startsWith("battery_f:") -> {
                                "%.${s.substring(10)}f".format(context.vsh.getBatteryLevel() * 100)
                            }
                            s == "battery_f" -> {
                                "%f".format(vsh.getBatteryLevel() * 100)
                            }
                            else -> {
                                "{$s}"
                            }
                        }
                    }
                })
            }
        }
        return sb.toString()
    }

    private fun getCompactDateTimeText(calendar: Calendar = Calendar.getInstance()): String {
        val timePattern = if (DateFormat.is24HourFormat(context)) "H:mm" else "h:mma"
        return SimpleDateFormat("M/dd $timePattern", Locale.getDefault()).format(calendar.time)
    }

    private fun getStatusText(): String {
        val formatSrc = dateTimeFormat.trim()
        val now = System.currentTimeMillis()
        val nowSecond = now / 1000L
        val operator = if (showMobileOperator) M.network.operatorName else ""
        val battery = (vsh.getBatteryLevel() * 100).toInt()
        val charging = vsh.isBatteryCharging()
        val is24Hour = DateFormat.is24HourFormat(context)
        val cacheKey = "$formatSrc|$operator|$battery|$charging|$is24Hour"

        if (cachedStatusTextAtSecond == nowSecond && cachedStatusTextKey == cacheKey) {
            return cachedStatusText
        }

        val calendar = Calendar.getInstance().apply { timeInMillis = now }
        cachedStatusText = when {
            formatSrc.isEmpty() -> getCompactDateTimeText(calendar)
            formatSrc == LEGACY_DEFAULT_FORMAT -> getCompactDateTimeText(calendar)
            formatSrc == COMPACT_DEFAULT_FORMAT -> getCompactDateTimeText(calendar)
            else -> format(formatSrc).replace(whitespaceRegex, " ").trim()
        }
        cachedStatusTextAtSecond = nowSecond
        cachedStatusTextKey = cacheKey
        return cachedStatusText
    }

    private fun getBatterySegmentCount(level: Float): Int {
        return when {
            level <= 0.05f -> 0
            level <= 0.34f -> 1
            level <= 0.67f -> 2
            else -> 3
        }
    }

    fun reloadBatteryGlyphOverride() {
        batteryGlyphRef?.release()
        batteryGlyphRef = null
        batteryGlyphPath = null
        batteryGlyphResolved = false
        ensureBatteryGlyphOverride(force = true)
    }

    private fun ensureBatteryGlyphOverride(force: Boolean = false) {
        if (!force && batteryGlyphResolved) return
        batteryGlyphResolved = true

        val glyphFile = FileQuery(VshBaseDirs.VSH_RESOURCES_DIR)
            .atPath(BATTERY_GLYPH_DIR)
            .withNames(*VshResName.BATTERY_GLYPH)
            .withExtensionArray(VshResTypes.ICONS)
            .onlyIncludeExists(true)
            .execute(context.vsh)
            .firstOrNull { it.isFile }

        val nextPath = glyphFile?.absolutePath
        if (nextPath == batteryGlyphPath) return

        batteryGlyphRef?.release()
        batteryGlyphPath = nextPath
        batteryGlyphRef = glyphFile?.let { file ->
            BitmapRef(
                "status_battery_glyph:${file.absolutePath}:${file.lastModified()}",
                { BitmapFactory.decodeFile(file.absolutePath) }
            )
        }
    }

    private fun drawBatteryGlyphShadow(ctx: Canvas, left: Float, top: Float, right: Float, bottom: Float): Boolean {
        ensureBatteryGlyphOverride()
        val ref = batteryGlyphRef ?: return false
        if (!ref.isLoaded) return false
        val shadowDx = (right - left) * 0.038f
        val shadowDy = (bottom - top) * 0.07f
        tmpRectF.set(left + shadowDx, top + shadowDy, right + shadowDx, bottom + shadowDy)
        ctx.drawBitmap(ref.bitmap, null, tmpRectF, batteryGlyphShadowPaint)
        return true
    }

    private fun drawBatteryGlyphOverride(ctx: Canvas, left: Float, top: Float, right: Float, bottom: Float): Boolean {
        ensureBatteryGlyphOverride()
        val ref = batteryGlyphRef ?: return false
        if (!ref.isLoaded) return false
        tmpRectF.set(left, top, right, bottom)
        ctx.drawBitmap(ref.bitmap, null, tmpRectF, bitmapPaint)
        return true
    }

    private fun hasBatteryGlyphOverride(): Boolean {
        ensureBatteryGlyphOverride()
        return batteryGlyphRef?.isLoaded == true
    }

    private fun drawBatterySegments(
        ctx: Canvas,
        shellLeft: Float,
        shellTop: Float,
        shellRight: Float,
        shellBottom: Float,
        batteryLevel: Float,
        color: Int,
        customFrame: Boolean
    ) {
        val segmentCount = getBatterySegmentCount(batteryLevel)
        if (segmentCount <= 0) return

        val shellWidth = shellRight - shellLeft
        val shellHeight = shellBottom - shellTop
        val segmentTop = shellTop + (shellHeight * 0.23f)
        val segmentBottom = shellBottom - (shellHeight * 0.23f)
        val segmentLeft = if (customFrame) {
            shellLeft + (shellWidth * 0.270f)
        } else {
            shellLeft + (shellHeight * 0.48f)
        }
        val segmentRight = if (customFrame) {
            shellLeft + (shellWidth * 0.875f)
        } else {
            shellRight - (shellHeight * 0.48f)
        }
        val segmentGap = shellWidth * customFrame.select(0.045f, 0.055f)
        val segmentWidth = ((segmentRight - segmentLeft) - (segmentGap * 2.0f)) / 3.0f
        val radius = shellHeight * 0.06f
        val visibleSegments = segmentCount.coerceAtMost(3)
        val startSlot = 3 - visibleSegments

        statusFillPaint.color = color
        for (slot in startSlot until 3) {
            val left = segmentLeft + (slot * (segmentWidth + segmentGap))
            tmpRectF.set(left, segmentTop, left + segmentWidth, segmentBottom)
            ctx.drawRoundRect(tmpRectF, radius, radius, statusFillPaint)
        }
    }

    private fun drawBatteryChargingBolt(
        ctx: Canvas,
        innerLeft: Float,
        innerTop: Float,
        innerRight: Float,
        innerBottom: Float,
        shellHeight: Float,
        color: Int
    ) {
        val centerY = (innerTop + innerBottom) / 2.0f
        val boltMidX = (innerLeft + innerRight) / 2.0f
        val boltTop = innerTop - (shellHeight * 0.025f)
        val boltBottom = innerBottom + (shellHeight * 0.025f)

        tmpPath.reset()
        tmpPath.moveTo(boltMidX - (shellHeight * 0.09f), boltTop)
        tmpPath.lineTo(boltMidX + (shellHeight * 0.025f), boltTop)
        tmpPath.lineTo(boltMidX - (shellHeight * 0.035f), centerY - (shellHeight * 0.01f))
        tmpPath.lineTo(boltMidX + (shellHeight * 0.1f), centerY - (shellHeight * 0.01f))
        tmpPath.lineTo(boltMidX - (shellHeight * 0.065f), boltBottom)
        tmpPath.lineTo(boltMidX - (shellHeight * 0.015f), centerY + (shellHeight * 0.03f))
        tmpPath.lineTo(boltMidX - (shellHeight * 0.12f), centerY + (shellHeight * 0.03f))
        tmpPath.close()
        statusFillPaint.color = color
        ctx.drawPath(tmpPath, statusFillPaint)
    }

    private fun drawDefaultBatteryBackdrop(
        ctx: Canvas,
        shellLeft: Float,
        shellTop: Float,
        shellRight: Float,
        shellBottom: Float
    ) {
        val radius = (shellBottom - shellTop) * 0.5f
        tmpRectF.set(shellLeft, shellTop, shellRight, shellBottom)
        ctx.drawRoundRect(tmpRectF, radius, defaultBatteryBackdropPaint)
    }

    private fun drawPspBatteryIcon(ctx: Canvas, right: Float, centerY: Float, shellWidth: Float, shellHeight: Float) {
        val batteryLevel = context.vsh.getBatteryLevel().coerceIn(0.0f, 1.0f)
        val charging = context.vsh.isBatteryCharging()
        val shellColor = when {
            batteryLevel <= 0.05f -> Color.argb(220, 255, 220, 220)
            batteryLevel <= 0.20f -> Color.argb(216, 248, 236, 236)
            else -> Color.argb(208, 240, 242, 246)
        }
        val fillColor = when {
            charging -> Color.argb(216, 250, 252, 255)
            batteryLevel <= 0.05f -> Color.argb(192, 255, 214, 214)
            batteryLevel <= 0.20f -> Color.argb(196, 248, 232, 232)
            else -> Color.argb(188, 236, 238, 244)
        }

        val shellLeft = right - shellWidth
        val shellTop = centerY - (shellHeight / 2.0f)
        val shellRight = right
        val shellBottom = centerY + (shellHeight / 2.0f)
        val radius = shellHeight * 0.5f
        val hasCustomGlyph = hasBatteryGlyphOverride()

        val innerPadX = shellHeight * 0.28f
        val innerPadY = shellHeight * 0.24f
        val innerLeft = shellLeft + innerPadX
        val innerTop = shellTop + innerPadY
        val innerRight = shellRight - innerPadX
        val innerBottom = shellBottom - innerPadY

        if (hasCustomGlyph) {
            drawBatteryGlyphShadow(ctx, shellLeft, shellTop, shellRight, shellBottom)
        } else {
            drawDefaultBatteryBackdrop(ctx, shellLeft, shellTop, shellRight, shellBottom)
        }

        val segmentColor = if (hasCustomGlyph) Color.argb(218, 255, 255, 255) else fillColor
        drawBatterySegments(ctx, shellLeft, shellTop, shellRight, shellBottom, batteryLevel, segmentColor, hasCustomGlyph)

        if (hasCustomGlyph) {
            drawBatteryGlyphOverride(ctx, shellLeft, shellTop, shellRight, shellBottom)
        } else {
            statusOutlinePaint.color = shellColor
            statusOutlinePaint.strokeWidth = 1.55f
            tmpRectF.set(shellLeft, shellTop, shellRight, shellBottom)
            ctx.drawRoundRect(tmpRectF, radius, radius, statusOutlinePaint)
        }

        if (charging && !hasCustomGlyph) {
            drawBatteryChargingBolt(ctx, innerLeft, innerTop, innerRight, innerBottom, shellHeight, Color.argb(196, 255, 255, 255))
        }
    }

    override fun render(ctx: Canvas){
        if(disabled) return
        when(view.screens.mainMenu.layoutMode){
            XmbLayoutType.PS3 -> drawStatusBarPS3(ctx)
            XmbLayoutType.PSP -> drawStatusBarPSP(ctx)
            else -> {}
        }
    }

    private fun drawStatusBarPS3(ctx: Canvas){

        val top = scaling.target.top + (scaling.target.height() * 0.1f)
        val hSize = 40.0f / 2.0f
        val leftRound = (scaling.screen.width() > scaling.screen.height()).select(0.5f, 0.75f)
        baseDefRect.set(
                scaling.target.right - (scaling.target.width() * leftRound),
                top - hSize,
                scaling.viewport.right + 120.0f,
                top + hSize,
        )
        statusOutlinePaint.strokeWidth = 1.5f
        statusOutlinePaint.color = Color.WHITE
        statusFillPaint.setColorAndSize(FColor.argb(0.25f, 0.0f,0f,0f), 25.0f, android.graphics.Paint.Align.RIGHT)
        ctx.drawRoundRect(baseDefRect, 10.0f, statusFillPaint)
        ctx.drawRoundRect(baseDefRect, 10.0f, statusOutlinePaint)

        statusTextPaint.setColorAndSize(Color.WHITE, 20.0f, android.graphics.Paint.Align.RIGHT)

        val status = StringBuilder()

        status.append(getStatusText())

        ctx.drawText(
                status.toString()
                , scaling.target.right - showAnalogClock.select(120.0f, 70.0f), top, statusTextPaint, 0.5f)

        if(showAnalogClock){
            view.widgets.analogClock.render(ctx)
        }
    }

    private fun drawStatusBarPSP(ctx: Canvas){
        val pspScale = scaling.target.height() / 272.0f
        fun pspS(value: Float) = value * pspScale

        val baselineY = scaling.viewport.top + pspS(pspPadStatusBar.select(10.6f, 8.4f))

        statusTextPaint.apply {
            setColorAndSize(Color.argb(218, 242, 244, 248), pspS(12.2f), Paint.Align.RIGHT)
            strokeWidth = 0.0f
            isFakeBoldText = false
            clearShadowLayer()
            isSubpixelText = true
            isLinearText = true
            letterSpacing = -0.01f
        }

        val textMetrics = statusTextPaint.fontMetrics
        val hasCustomGlyph = hasBatteryGlyphOverride()
        val batteryHeightBase = ((textMetrics.descent - textMetrics.ascent) * 0.76f).coerceIn(pspS(9.1f), pspS(11.6f))
        val batteryHeight = if (hasCustomGlyph) batteryHeightBase * 1.14f else batteryHeightBase
        val batteryWidth = batteryHeight * 2.15f
        val batteryGap = pspS(5.0f)
        val batteryRight = scaling.viewport.right - pspS(4.75f)
        val textRight = batteryRight - if (pspShowBattery) batteryWidth + batteryGap else 0.0f
        val batteryCenterYBase = baselineY + ((textMetrics.ascent + textMetrics.descent) / 2.0f) + pspS(6.05f)
        val batteryCenterY = batteryCenterYBase + if (hasCustomGlyph) pspS(0.15f) else 0.0f + 3.0f

        ctx.drawText(getStatusText(), textRight, baselineY, statusTextPaint, 0.5f)

        if(pspShowBattery){
            drawPspBatteryIcon(ctx, batteryRight, batteryCenterY, batteryWidth, batteryHeight)
        }
    }
}
