package id.psw.vshlauncher

import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.TextPaint
import androidx.annotation.ColorInt
import id.psw.vshlauncher.typography.FontCollections
import java.io.File

object VshBaseDirs {
    const val VSH_RESOURCES_DIR = "dev_flash/vsh/resource"
    const val FLASH_DATA_DIR = "dev_flash/data"
    const val USER_DIR = "dev_hdd0/home"
    const val LOGS_DIR = "dev_hdd1/logs"
    const val APPS_DIR = "dev_hdd0/game"
    const val PLUGINS_DIR = "dev_hdd0/plugins"
    const val SHORTCUTS_DIR = "dev_hdd0/shortcut"
    const val CACHE_DIR = "dev_hdd1/caches"
}

object VshResName {
    val COLDBOOT = arrayOf("coldboot", "COLDBOOT")
    val GAMEBOOT = arrayOf("gameboot", "GAMEBOOT")
    val BATTERY_GLYPH = arrayOf("battery", "BATTERY")
    const val APP_ICON = "ICON0"
    const val APP_ANIM_ICON = "ICON1"
}
object VshResTypes {
    val IMAGES = arrayOf("jpg","png","webp","jpeg","JPG","PNG","WEBP","JPEG")
    val ICONS = arrayOf("png","webp","jpg","jpeg","PNG","WEBP","JPG","JPEG")
    val ANIMATED_ICONS = arrayOf("webp","apng","gif", "WEBP","APNG", "GIF")
    val SOUNDS = arrayOf("AAC","OGG","MP3","WAV","MID","MIDI","aac","ogg","mp3","wav","mid","midi")
    val INI = arrayOf("ini","INI")
}

const val MAX_ANIMATED_ICON_FILE_BYTES = 4L * 1024L * 1024L
const val MAX_ANIMATED_ICON_FILE_SIZE_MB = 4

fun isSupportedAnimatedIconExtension(extension: String): Boolean {
    return extension.lowercase() in setOf("gif", "webp", "apng")
}

fun isLoadableAnimatedIconFile(file: File): Boolean {
    return file.isFile &&
        isSupportedAnimatedIconExtension(file.extension) &&
        file.length() in 1..MAX_ANIMATED_ICON_FILE_BYTES
}

fun Vsh.requireValidAnimatedIconImport(extension: String, sizeBytes: Long) {
    if (!isSupportedAnimatedIconExtension(extension)) {
        throw IllegalArgumentException(getString(R.string.error_anim_icon_unsupported_type))
    }
    if (sizeBytes > MAX_ANIMATED_ICON_FILE_BYTES) {
        throw IllegalArgumentException(
            getString(R.string.error_anim_icon_too_large).format(MAX_ANIMATED_ICON_FILE_SIZE_MB)
        )
    }
}

val ActivityInfo.uniqueActivityName get() = "${processName}_${name.removeSimilarPrefixes(processName)}"
val ResolveInfo.uniqueActivityName get() = activityInfo.uniqueActivityName

val Vsh.allCacheDirs : Array<File> get() {
    return arrayOf(cacheDir, *externalCacheDirs)
}

fun Vsh.makeTextPaint(
    size: Float = 12.0f,
    align: Paint.Align = Paint.Align.LEFT,
    @ColorInt color : Int = Color.WHITE,
    style : Paint.Style = Paint.Style.FILL
) : TextPaint {
    return TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textAlign = align
        textSize = size
        this.style = style
        typeface = FontCollections.masterFont
    }
}

fun String.removeSimilarPrefixes(b:String) : String{
    // Seek until it finds any differences
    var i = 0
    var isEqual = true
    while(i < kotlin.math.min(length, b.length) && isEqual){
        isEqual = get(i) == b[i]
        i++
    }
    // Seek until next dot
    var isDot = false
    while(i < length && !isDot){
        isDot = get(i) == '.'
        i++
    }
    val retval =if(i < length) substring(i) else b
    return retval
}

/** Basically Useless on Android 4.2+ when user is exclusively using the emulated internal storage,
 * since the base emulated storage path will always contains On-device User Index,
 * Hence the "/storage/emulated/{user_index}".
 * Unless user is using External SD Card or is using a device that still
 * mounts the emulated internal storage to "/mnt/media" (or something similar) instead of to
 * "/storage/emulated/{user_index}", this is useless.
 */
fun Vsh.getUserIdPath() : String {
    return "00000000"
}

val File.isOnInternalStorage : Boolean get() = absolutePath.startsWith("/storage/emulated")

/**
 * Scales a bitmap to fit within the specified dimensions while maintaining aspect ratio,
 * and centers it on a transparent canvas of those dimensions.
 */
fun Bitmap.fitCenter(width: Int, height: Int, recycleSource: Boolean = false): Bitmap {
    val srcWidth = this.width.toFloat()
    val srcHeight = this.height.toFloat()
    val scale = kotlin.math.min(width / srcWidth, height / srcHeight)
    val sw = (srcWidth * scale).toInt().coerceAtLeast(1)
    val sh = (srcHeight * scale).toInt().coerceAtLeast(1)
    val scaled = Bitmap.createScaledBitmap(this, sw, sh, true)
    val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    canvas.drawBitmap(scaled, (width - sw) / 2f, (height - sh) / 2f, null)
    if (scaled != this) scaled.recycle()
    if (recycleSource) this.recycle()
    return out
}

fun Bitmap.fitCenter(size: Int, recycleSource: Boolean = false): Bitmap = fitCenter(size, size, recycleSource)
