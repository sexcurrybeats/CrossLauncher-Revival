package id.psw.vshlauncher.views.dialogviews

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.app.ActivityCompat
import id.psw.vshlauncher.FittingMode
import id.psw.vshlauncher.R
import id.psw.vshlauncher.Vsh
import id.psw.vshlauncher.activities.Xmb
import id.psw.vshlauncher.postNotification
import id.psw.vshlauncher.submodules.PadKey
import id.psw.vshlauncher.types.XmbItem
import id.psw.vshlauncher.typography.FontCollections
import id.psw.vshlauncher.views.XmbDialogSubview
import id.psw.vshlauncher.views.XmbView
import id.psw.vshlauncher.views.drawBitmap

class SetWallpaperDialogView(v : XmbView, private val xmb: Xmb, private val intent: Intent) : XmbDialogSubview(v) {
    companion object {
        fun decodeWallpaperBitmap(context: Context, uri: Uri): Bitmap {
            return if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P){
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            }else{
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
        }

        fun setLauncherWallpaper(vsh: Vsh, bitmap: Bitmap): Boolean {
            val success = vsh.M.customizer.saveLauncherWallpaper(bitmap)
            if (success) {
                vsh.postNotification(null, "Wallpaper set", "Launcher wallpaper has been updated.", 4.0f)
            }
            return success
        }

        fun setLauncherWallpaper(vsh: Vsh, uri: Uri): Boolean {
            var bitmap: Bitmap? = null
            return try {
                val decoded = decodeWallpaperBitmap(vsh, uri)
                bitmap = decoded
                setLauncherWallpaper(vsh, decoded)
            } catch (e: Exception) {
                vsh.postNotification(null, "Wallpaper set failed", e.toString(), 5.0f)
                false
            } finally {
                bitmap?.recycle()
            }
        }

        private fun recycleAfterDeviceWallpaper(vsh: Vsh, xmb: Xmb, bitmap: Bitmap): Boolean {
            return try {
                setDeviceWallpaper(vsh, xmb, bitmap)
            } finally {
                bitmap.recycle()
            }
        }

        fun setDeviceWallpaper(vsh: Vsh, xmb: Xmb, bitmap: Bitmap): Boolean {
            if(!hasWallpaperPermission(vsh, xmb)){
                vsh.postNotification(null,
                    "Permission not granted",
                    "If you have granted the permission on the dialog previously shown, please use press button for Install once again.", 10.0f)
                return false
            }

            val wpman = vsh.getSystemService(Context.WALLPAPER_SERVICE) as WallpaperManager
            wpman.setBitmap(bitmap)
            vsh.postNotification(null, "Wallpaper set", "Device wallpaper has been updated.", 4.0f)
            return true
        }

        fun setDeviceWallpaper(vsh: Vsh, xmb: Xmb, uri: Uri): Boolean {
            return try {
                recycleAfterDeviceWallpaper(vsh, xmb, decodeWallpaperBitmap(vsh, uri))
            } catch (e: Exception) {
                vsh.postNotification(null, "Wallpaper set failed", e.toString(), 5.0f)
                false
            }
        }

        private fun hasWallpaperPermission(vsh: Vsh, xmb: Xmb) : Boolean{
            return if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
                val r = vsh.checkSelfPermission(android.Manifest.permission.SET_WALLPAPER) == PackageManager.PERMISSION_GRANTED
                if(!r && ActivityCompat.shouldShowRequestPermissionRationale(xmb, android.Manifest.permission.SET_WALLPAPER)){
                    ActivityCompat.requestPermissions(xmb, arrayOf(android.Manifest.permission.SET_WALLPAPER), 12984)
                }
                r
            } else true
        }
    }

    override val hasNegativeButton: Boolean = true
    override val hasPositiveButton: Boolean = true
    override val icon: Bitmap get() = XmbItem.TRANSPARENT_BITMAP
    override val title: String get() = vsh.getString(R.string.dlg_set_wallpaper_title)

    private var isInternal = false
    private var imageLoaded = false
    private var image : Bitmap = XmbItem.TRANSPARENT_BITMAP
    private var bmpPaint = Paint().apply {

    }
    private var txtPaint = Paint().apply {
        typeface = FontCollections.masterFont
        textSize = 20.0f
        color = Color.WHITE

        textAlign = Paint.Align.CENTER
    }

    override val positiveButton: String
        get() = vsh.getString(R.string.common_install)
    override val negativeButton: String
        get() = vsh.getString(android.R.string.cancel)

    override fun onDraw(ctx: Canvas, drawBound: RectF, deltaTime:Float) {
        ctx.drawBitmap(image, null, drawBound, bmpPaint, FittingMode.FILL)
        ctx.drawARGB(128,0,0,0)

        arrayOf(
            vsh.getString(R.string.media_set_as_launcher_wallpaper),
            vsh.getString(R.string.media_set_as_device_wallpaper)
        ).forEachIndexed{ i, it ->
            val selected = isInternal == (i == 0)
            if(selected){
                txtPaint.setShadowLayer(10.0f, 0.0f, 0.0f, Color.WHITE)
            }else{
                txtPaint.setShadowLayer(0.0f, 0.0f, 0.0f, Color.TRANSPARENT)
            }

            ctx.drawText(it, drawBound.centerX(), drawBound.centerY()+(i * (txtPaint.textSize * 1.25f)), txtPaint)
        }
    }

    override fun onDialogButton(isPositive: Boolean) {
        if(isPositive){
            if(isInternal){
                if(setLauncherWallpaper(vsh, image)){
                    finish(view.screens.mainMenu)
                }
            }else{
                if(setDeviceWallpaper(vsh, xmb, image)){
                    Thread.sleep(1000L) // Wait for system to save the image into the system
                    finish(view.screens.mainMenu)
                }
            }
        }else{
            finish(view.screens.mainMenu)
        }
    }

    override fun onClose() {
        if(imageLoaded){
            image.recycle()
        }
    }

    override fun onGamepad(key: PadKey, isPress: Boolean): Boolean {
        if(isPress){
            if(key == PadKey.PadU || key == PadKey.PadD){
                isInternal = !isInternal
                return true
            }
        }
        return super.onGamepad(key, isPress)
    }

    override fun onStart() {
        val uri = intent.clipData?.getItemAt(0)?.uri
        try{
            if(uri != null){
                image = decodeWallpaperBitmap(vsh, uri)
                imageLoaded = true
            }
        }catch(e:Exception){
            vsh.postNotification(null, "Wallpaper decode failed",e.toString(), 5.0f)
            finish(view.screens.mainMenu)
        }

    }

    override fun onTouch(a: PointF, b: PointF, act: Int) {

    }
}
