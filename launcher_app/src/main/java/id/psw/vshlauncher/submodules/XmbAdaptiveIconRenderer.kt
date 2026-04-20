package id.psw.vshlauncher.submodules

import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.*
import android.graphics.drawable.*
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import id.psw.vshlauncher.*
import id.psw.vshlauncher.types.FileQuery
import id.psw.vshlauncher.types.Ref
import java.io.File
import java.lang.Exception

class XmbAdaptiveIconRenderer(private val ctx: Vsh) : IVshSubmodule {

    companion object {
        private const val TAG = "XMBIconGen"
        var BaseWidth = 120
        var BaseHeight = 66
        var WIDTH = BaseWidth
        var HEIGHT = BaseHeight
        val Width get() = WIDTH
        val Height get() = HEIGHT

        /**
         * Setting for adaptive renderer
         *
         * values:
         * - Scaling: -1 = 0%, 0.0 = Fit, 1.0 = Fill, 2.0 = 200% Fill
         * - Anchor: 0.0 = Left, 0.5 = Center, 1.0 = Right
         */
        object AdaptiveRenderSetting {
            var ForeScale = 1.0f
            var BackScale = 1.0f
            var ForeYAnchor = 0.5f
            var BackYAnchor = 0.5f
            var ForeXAnchor = 0.5f
            var BackXAnchor = 0.5f
            var LegacyScale = 0.0f
            var LegacyXAnchor = 0.5f
            var LegacyYAnchor = 0.5f
            var legacyBg = 0
            var legacyBgYouA = 0
            var legacyBgYouB = 0
            var legacyBgColor = 0x7FFFFFFF
            var wideCard = true
            /** Usage : Leftmost 2 bits is highest
             - 00 = Application Icon
             - 01 = Adaptive Application Icon
             - 10 = Application Banner (Usually used by Android TV Launcher)
             - 11 = Adaptive Application Banner
             Highest means if available then use, if not, use the lower
            */
            var iconPriority = 0b01111000
        }
        const val ICON_PRIORITY_TYPE_APP_ICON_LEGACY = 0b00
        const val ICON_PRIORITY_TYPE_APP_ICON_ADAPTIVE = 0b01
        const val ICON_PRIORITY_TYPE_APP_BANNER_LEGACY = 0b10
        const val ICON_PRIORITY_TYPE_APP_BANNER_ADAPTIVE = 0b11

        fun getIconPriorityAt(at:Int) : Int{
            return (AdaptiveRenderSetting.iconPriority shr ((3 - at) * 2)) and 0b11
        }
    }

    private lateinit var pm : PackageManager
    private val fileRoots = ArrayList<File>()
    private val materialYouColor = Ref(0)
    private var supportsMaterialYou = false

    override fun onCreate() {
        pm = ctx.packageManager
        supportsMaterialYou = getMaterialYouColor(ctx, 0, 0, materialYouColor)
        val d = ctx.resources.displayMetrics.density
        WIDTH = (BaseWidth * d).toInt()
        HEIGHT = (BaseHeight * d).toInt()
        val mSb = StringBuilder()
        mSb.appendLine("Icon file source : ")

        ctx.M.apps.tryMigrateOldGameDirectory()

        FileQuery(VshBaseDirs.APPS_DIR).createParentDirectory(true).execute(ctx).forEach {
            fileRoots.add(it)
            mSb.appendLine(it.absolutePath)
        }
        Logger.d(TAG, mSb.toString())
        readPreferences()
    }

    override fun onDestroy() {
        // TODO: Do memory cleanup
    }

    fun readPreferences(){
        AdaptiveRenderSetting.legacyBg = ctx.M.pref.get(PrefEntry.ICON_RENDERER_LEGACY_BACKGROUND, 0)
        AdaptiveRenderSetting.legacyBgColor = ctx.M.pref.get(PrefEntry.ICON_RENDERER_LEGACY_BACK_COLOR, 0x7FFFFFFF)
        AdaptiveRenderSetting.wideCard = ctx.M.pref.get(PrefEntry.ICON_RENDERER_WIDE_CARD, true)
        val you = ctx.M.pref.get(PrefEntry.ICON_RENDERER_LEGACY_BACK_MATERIAL_YOU, 0)
        AdaptiveRenderSetting.legacyBgYouA = you / 100
        AdaptiveRenderSetting.legacyBgYouB = you % 100
    }

    private fun drawFittedBitmap(c:Canvas, d:Drawable?, scale:Float, xAnchor:Float, yAnchor:Float, drawRect:RectF){
        var b : Bitmap? = null
        if(d != null){
            val fw = WIDTH.toFloat()
            val fh = HEIGHT.toFloat()
            b = if(d.hasSize){
                val fbScale = fitFillSelect(
                    d.intrinsicWidth.toFloat(), fw,
                    d.intrinsicHeight.toFloat(), fh,
                    fw, fh,
                    scale
                )
                d.toBitmap(
                    (fbScale * d.intrinsicWidth).toInt(),
                    (fbScale * d.intrinsicHeight).toInt(),
                    Bitmap.Config.ARGB_8888);
            } else {
                d.toBitmap(WIDTH, HEIGHT)
            }
        }
        if(b != null){
            drawRect.left = xAnchor.toLerp(0.0f, WIDTH.toFloat() - b.width )
            drawRect.top = yAnchor.toLerp(0.0f, HEIGHT.toFloat() - b.height)
            drawRect.right = drawRect.left + b.width
            drawRect.bottom = drawRect.top + b.height
            c.drawBitmap(b, null, drawRect, null)
        }
    }

    private fun isSquareLike(drawable: Drawable?): Boolean {
        if (drawable == null) return false
        val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: WIDTH
        val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: HEIGHT
        if (h == 0) return false
        val ratio = w.toFloat() / h.toFloat()
        return ratio in 0.8f..1.2f
    }

    private fun createRenderedBitmap(drawable: Drawable?, width: Int, height: Int, fillBounds: Boolean = false): Bitmap? {
        if (drawable == null || width <= 0 || height <= 0) return null
        return try {
            if (fillBounds || !drawable.hasSize) {
                drawable.toBitmap(width, height, Bitmap.Config.ARGB_8888)
            } else {
                val srcWidth = drawable.intrinsicWidth.toFloat()
                val srcHeight = drawable.intrinsicHeight.toFloat()
                val scale = kotlin.math.min(width / srcWidth, height / srcHeight)
                val drawWidth = (srcWidth * scale).toInt().coerceAtLeast(1)
                val drawHeight = (srcHeight * scale).toInt().coerceAtLeast(1)
                val left = ((width - drawWidth) / 2.0f).toInt()
                val top = ((height - drawHeight) / 2.0f).toInt()
                val oldBounds = Rect(drawable.bounds)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)

                drawable.setBounds(left, top, left + drawWidth, top + drawHeight)
                drawable.draw(canvas)
                drawable.bounds = oldBounds
                bitmap
            }
        } catch (_: Exception) {
            null
        }
    }

    private data class CardPalette(val base: Int, val edge: Int, val hasOpaquePixels: Boolean)

    private fun buildCardPalette(src: Bitmap?): CardPalette {
        if (src == null) {
            return CardPalette(Color.argb(255, 40, 40, 48), Color.argb(255, 70, 70, 82), false)
        }

        var totalA = 0L
        var totalR = 0L
        var totalG = 0L
        var totalB = 0L
        var edgeA = 0L
        var edgeR = 0L
        var edgeG = 0L
        var edgeB = 0L

        val width = src.width
        val height = src.height
        val edgeInset = (kotlin.math.min(width, height) * 0.18f).toInt().coerceAtLeast(1)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val color = src.getPixel(x, y)
                val alpha = Color.alpha(color)
                if (alpha <= 16) continue

                totalA += alpha
                totalR += Color.red(color) * alpha.toLong()
                totalG += Color.green(color) * alpha.toLong()
                totalB += Color.blue(color) * alpha.toLong()

                if (x < edgeInset || y < edgeInset || x >= width - edgeInset || y >= height - edgeInset) {
                    edgeA += alpha
                    edgeR += Color.red(color) * alpha.toLong()
                    edgeG += Color.green(color) * alpha.toLong()
                    edgeB += Color.blue(color) * alpha.toLong()
                }
            }
        }

        if (totalA <= 0L) {
            return CardPalette(Color.argb(255, 40, 40, 48), Color.argb(255, 70, 70, 82), false)
        }

        val baseColor = Color.rgb(
            (totalR / totalA).toInt().coerceIn(0, 255),
            (totalG / totalA).toInt().coerceIn(0, 255),
            (totalB / totalA).toInt().coerceIn(0, 255)
        )

        val edgeColor = if (edgeA > 0L) {
            Color.rgb(
                (edgeR / edgeA).toInt().coerceIn(0, 255),
                (edgeG / edgeA).toInt().coerceIn(0, 255),
                (edgeB / edgeA).toInt().coerceIn(0, 255)
            )
        } else {
            baseColor
        }

        return CardPalette(baseColor, edgeColor, true)
    }

    private fun blendColor(color: Int, target: Int, amount: Float): Int {
        val t = amount.coerceIn(0.0f, 1.0f)
        val inv = 1.0f - t
        return Color.argb(
            ((Color.alpha(color) * inv) + (Color.alpha(target) * t)).toInt().coerceIn(0, 255),
            ((Color.red(color) * inv) + (Color.red(target) * t)).toInt().coerceIn(0, 255),
            ((Color.green(color) * inv) + (Color.green(target) * t)).toInt().coerceIn(0, 255),
            ((Color.blue(color) * inv) + (Color.blue(target) * t)).toInt().coerceIn(0, 255)
        )
    }

    private fun drawGeneratedWideCardBackground(canvas: Canvas, sourceBitmap: Bitmap?) {
        val palette = buildCardPalette(sourceBitmap)
        val topColor = blendColor(palette.edge, Color.WHITE, 0.22f)
        val bottomColor = blendColor(palette.base, Color.BLACK, 0.28f)
        val rect = RectF(0.0f, 0.0f, WIDTH.toFloat(), HEIGHT.toFloat())
        val radius = HEIGHT * 0.12f

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0.0f,
                0.0f,
                0.0f,
                HEIGHT.toFloat(),
                topColor,
                bottomColor,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(rect, radius, radius, bgPaint)

        if (palette.hasOpaquePixels && sourceBitmap != null) {
            val stretched = Bitmap.createScaledBitmap(sourceBitmap, WIDTH, HEIGHT, true)
            val shrunken = Bitmap.createScaledBitmap(
                stretched,
                (WIDTH / 8).coerceAtLeast(1),
                (HEIGHT / 8).coerceAtLeast(1),
                true
            )
            val blurred = Bitmap.createScaledBitmap(shrunken, WIDTH, HEIGHT, true)
            val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { alpha = 72 }
            canvas.drawBitmap(blurred, 0.0f, 0.0f, glowPaint)
            blurred.recycle()
            shrunken.recycle()
            stretched.recycle()
        }

        val glossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0.0f,
                0.0f,
                0.0f,
                HEIGHT * 0.55f,
                Color.argb(42, 255, 255, 255),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(rect, radius, radius, glossPaint)
    }

    private fun drawCenteredBitmap(canvas: Canvas, bitmap: Bitmap, scaleFactor: Float = 0.82f) {
        val targetHeight = (HEIGHT * scaleFactor).toInt().coerceAtLeast(1)
        val targetWidth = (WIDTH * 0.46f).toInt().coerceAtLeast(1)
        val scale = kotlin.math.min(
            targetWidth.toFloat() / bitmap.width.toFloat(),
            targetHeight.toFloat() / bitmap.height.toFloat()
        ).coerceAtMost(1.0f)

        val drawW = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val drawH = (bitmap.height * scale).toInt().coerceAtLeast(1)
        val left = (WIDTH - drawW) / 2.0f
        val top = (HEIGHT - drawH) / 2.0f
        canvas.drawBitmap(bitmap, null, RectF(left, top, left + drawW, top + drawH), null)
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private fun drawAdaptiveWideCard(canvas: Canvas, icon: AdaptiveIconDrawable) {
        val fgBitmap = createRenderedBitmap(icon.foreground, HEIGHT, HEIGHT)
        val bgBitmap = createRenderedBitmap(icon.background, WIDTH, HEIGHT, fillBounds = true)

        if (bgBitmap != null) {
            canvas.drawBitmap(bgBitmap, 0.0f, 0.0f, null)
            bgBitmap.recycle()
            val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0.0f,
                    0.0f,
                    0.0f,
                    HEIGHT.toFloat(),
                    Color.argb(20, 255, 255, 255),
                    Color.argb(36, 0, 0, 0),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0.0f, 0.0f, WIDTH.toFloat(), HEIGHT.toFloat(), scrimPaint)
        } else {
            drawGeneratedWideCardBackground(canvas, fgBitmap)
        }

        if (fgBitmap != null) {
            drawCenteredBitmap(canvas, fgBitmap)
            fgBitmap.recycle()
        }
    }

    private fun drawLegacyWideCard(canvas: Canvas, legacyIcon: Drawable) {
        val sourceBitmap = createRenderedBitmap(legacyIcon, HEIGHT, HEIGHT)
        drawGeneratedWideCardBackground(canvas, sourceBitmap)
        if (sourceBitmap != null) {
            drawCenteredBitmap(canvas, sourceBitmap)
            sourceBitmap.recycle()
        }
    }


    @RequiresApi(api = Build.VERSION_CODES.O)
    private fun drawAdaptive(ctx:Canvas, icon : Drawable, banner : Drawable?){
        val drawRect = emptyRectF
        with(AdaptiveRenderSetting){
            var isBannerUsed = false
            if(banner.hasSize){
                isBannerUsed = banner?.intrinsicHeight != banner?.intrinsicWidth
            }

            for(i in 0 .. 3){
                val prio = getIconPriorityAt(i)
                when(prio){
                    ICON_PRIORITY_TYPE_APP_ICON_ADAPTIVE -> {
                        if(icon is AdaptiveIconDrawable){
                            if (AdaptiveRenderSetting.wideCard) {
                                drawAdaptiveWideCard(ctx, icon)
                            } else {
                                drawFittedBitmap(ctx, icon.background, BackScale, BackXAnchor, BackYAnchor, drawRect)
                                drawFittedBitmap(ctx, icon.foreground, ForeScale, ForeXAnchor, ForeYAnchor, drawRect)
                            }
                            return
                        }
                    }
                    ICON_PRIORITY_TYPE_APP_BANNER_LEGACY -> {
                        if(isBannerUsed && banner != null && banner.hasSize){
                            drawLegacy(ctx, banner)
                            return
                        }
                    }
                    ICON_PRIORITY_TYPE_APP_BANNER_ADAPTIVE -> {
                        if(isBannerUsed && banner is AdaptiveIconDrawable)
                        {
                            drawFittedBitmap(ctx, banner.background, BackScale, BackXAnchor, BackYAnchor, drawRect)
                            drawFittedBitmap(ctx, banner.foreground, ForeScale, ForeXAnchor, ForeYAnchor, drawRect)
                            return
                        }
                    }
                    ICON_PRIORITY_TYPE_APP_ICON_LEGACY -> {
                        // Legacy Icon is absolute fallback
                        drawLegacy(ctx, icon)
                        return
                    }
                }
            }
        }
    }

    private val emptyRectF =RectF()
    private fun drawLegacy(ctx:Canvas, legacyIcon:Drawable){
        val that = this.ctx
        with(AdaptiveRenderSetting)
        {
            if (wideCard && isSquareLike(legacyIcon)) {
                drawLegacyWideCard(ctx, legacyIcon)
                return
            }

            supportsMaterialYou = getMaterialYouColor(that, legacyBgYouA, legacyBgYouB, materialYouColor)

            val color = (legacyBg == 2 && supportsMaterialYou).select(materialYouColor.p, legacyBgColor)

            if(legacyBg != 0){
                ctx.drawARGB(
                    (Color.alpha(color) shl 1 or 1),
                    Color.red(color),
                    Color.green(color),
                    Color.blue(color)
                )
            }

            drawFittedBitmap(ctx, legacyIcon, LegacyScale, LegacyXAnchor, LegacyYAnchor, emptyRectF)
        }
    }

    private fun loadCustomIcon(act:ActivityInfo) :Bitmap?{
        fileRoots.forEach {
            val f = it.combine(act.uniqueActivityName, "ICON0.PNG")
            if(f?.exists() == true){
                try{
                    val b = BitmapFactory.decodeFile(f.absolutePath)
                    if( b != null) return b
                }catch (e:Exception){
                    Logger.e(TAG, "Cannot decode custom image : ${f.absolutePath}")
                }
            }
        }
        return null
    }

    fun create(act:ActivityInfo, vsh:Vsh) : Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        val canvas = Canvas(bitmap)
        val custom = loadCustomIcon(act)

        if(custom != null){
            bitmap.recycle()
            return custom
        }else{
            var d = act.loadIcon(pm)
            val spResRef = Ref<Resources>(vsh.resources)
            val spResIdRef = Ref(0)
            val apkHasSpecialRes = hasSpecialRes(act, vsh, spResRef, spResIdRef)

            if(apkHasSpecialRes){
                d = ResourcesCompat.getDrawable(spResRef.p, spResIdRef.p, null)
            }

            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !apkHasSpecialRes){
                val b = act.loadBanner(pm)
                drawAdaptive(canvas, d, b)
            }else{
                drawLegacy(canvas, d)
            }
        }

        return bitmap
    }

    /**
     * Check if app package contains Cross Launcher-specific icon
     *
     * Developer may add one by putting a drawable with name of "psw_crosslauncher_banner" (R.drawable.psw_crosslauncher_banner)
     * in their app package.
     */
    private fun hasSpecialRes(act: ActivityInfo, vsh: Vsh, spResRef: Ref<Resources>, spResIdRef : Ref<Int>): Boolean {
        try{
            spResRef.p = vsh.packageManager.getResourcesForApplication(act.applicationInfo)
            spResIdRef.p = spResRef.p.getIdentifier("psw_crosslauncher_banner", "drawable", act.packageName)
            return spResIdRef.p != 0
        }catch(e:Exception){ }
        return false
    }
}
