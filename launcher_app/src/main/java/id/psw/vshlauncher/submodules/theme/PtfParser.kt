package id.psw.vshlauncher.submodules.theme

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import id.psw.vshlauncher.Vsh
import id.psw.vshlauncher.types.XmbTheme
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Parser for `.ptf` theme package format.
 * PTF is a binary container for theme resources.
 *
 * Layout:
 * - Header (16 bytes): "PTF\0", version, etc.
 * - Table of Contents: Entry metadata (ID, Offset, Size, Type)
 * - Data Blobs: Bitmaps (GIM/PNG/JPG), XML, etc.
 */
class PtfParser(private val vsh: Vsh) {

    data class PtfEntry(
        val id: Int,
        val offset: Int,
        val size: Int,
        val type: Int
    )

    fun parse(file: File): XmbTheme? {
        if (!file.exists()) return null
        return file.inputStream().use { parse(it, file.nameWithoutExtension) }
    }

    fun parse(stream: InputStream, themeId: String): XmbTheme? {
        val bytes = stream.readBytes()
        if (bytes.size < 16) return null

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        // Magic: "PTF\0"
        if (buffer.get() != 'P'.code.toByte() || buffer.get() != 'T'.code.toByte() ||
            buffer.get() != 'F'.code.toByte() || buffer.get() != 0.toByte()) {
            return null
        }

        // PTF parsing is complex because it often contains GIM images.
        // For the MVP, we'll focus on extracting the theme metadata and common assets.
        // A full implementation would require a GIM to Bitmap converter.

        val theme = XmbTheme(
            id = "ptf_$themeId",
            name = themeId,
            description = "Imported from PTF"
        )

        // TODO: Iterate TOC and extract resources
        // Mapping common PTF IDs:
        // 0x01: Icon0 (Preview)
        // 0x02: Wallpaper
        // 0x10-0x17: Category Icons

        return theme
    }

    private fun decodeGim(bytes: ByteArray): Bitmap? {
        // GIM is a proprietary image container.
        // Many PTF files use standard PNG/JPG if they were made with newer tools.
        return try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        }
    }
}
