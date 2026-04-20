package id.psw.vshlauncher.livewallpaper

import android.content.Context
import id.psw.vshlauncher.Logger
import java.util.Locale
import kotlin.math.abs

object PspXmbGradientPresets {
    private const val TAG = "public_xmb_gradients"
    const val PRESET_NONE = 0
    const val PRESET_COUNT = 34

    fun displayName(index: Int): String {
        return if (index in 1..PRESET_COUNT) {
            "Public Gradient %02d".format(Locale.US, index)
        } else {
            "Off"
        }
    }

    fun nextIndex(index: Int): Int {
        return if (index >= PRESET_COUNT) PRESET_NONE else (index + 1).coerceAtLeast(PRESET_NONE)
    }

    fun applyToNative(context: Context, index: Int) {
        if (index !in 1..PRESET_COUNT) {
            NativeGL.clearBackgroundTexture()
            return
        }

        try {
            val bmp = context.assets.open(assetPath(index)).use { stream ->
                decodeBmp24(stream.readBytes())
            }
            NativeGL.setBackgroundTexture(bmp.width, bmp.height, bmp.pixels)
        } catch (e: Exception) {
            Logger.w(TAG, "Failed to load ${displayName(index)}: ${e.message}")
            NativeGL.clearBackgroundTexture()
        }
    }

    private fun assetPath(index: Int): String {
        return "public_xmb_gradients/raw_60x34/%02d_raw.bmp".format(Locale.US, index)
    }

    private data class GradientBitmap(
        val width: Int,
        val height: Int,
        val pixels: IntArray
    )

    private fun decodeBmp24(bytes: ByteArray): GradientBitmap {
        require(bytes.size >= 54) { "BMP header is too small" }
        require(bytes[0] == 'B'.code.toByte() && bytes[1] == 'M'.code.toByte()) { "Not a BMP file" }

        val dataOffset = leInt(bytes, 10)
        val dibSize = leInt(bytes, 14)
        require(dibSize >= 40) { "Unsupported BMP DIB header" }

        val width = leInt(bytes, 18)
        val rawHeight = leInt(bytes, 22)
        val planes = leShort(bytes, 26)
        val bitsPerPixel = leShort(bytes, 28)
        val compression = leInt(bytes, 30)
        require(width > 0 && rawHeight != 0) { "Invalid BMP dimensions" }
        require(planes == 1 && bitsPerPixel == 24 && compression == 0) {
            "Only uncompressed 24-bit BMP gradients are supported"
        }

        val height = abs(rawHeight)
        val bottomUp = rawHeight > 0
        val rowStride = ((bitsPerPixel * width + 31) / 32) * 4
        require(dataOffset + (rowStride * height) <= bytes.size) { "BMP pixel data is truncated" }

        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val sourceY = if (bottomUp) height - 1 - y else y
            val rowStart = dataOffset + (sourceY * rowStride)
            for (x in 0 until width) {
                val offset = rowStart + (x * 3)
                val b = bytes[offset].toInt() and 0xFF
                val g = bytes[offset + 1].toInt() and 0xFF
                val r = bytes[offset + 2].toInt() and 0xFF
                pixels[(y * width) + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return GradientBitmap(width, height, pixels)
    }

    private fun leShort(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun leInt(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }
}
