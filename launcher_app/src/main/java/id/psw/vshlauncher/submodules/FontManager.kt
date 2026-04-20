package id.psw.vshlauncher.submodules

import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import id.psw.vshlauncher.PrefEntry
import id.psw.vshlauncher.R
import id.psw.vshlauncher.Vsh
import id.psw.vshlauncher.VshBaseDirs
import id.psw.vshlauncher.types.FileQuery
import id.psw.vshlauncher.typography.FontCollections
import java.io.File

class FontManager(private val vsh: Vsh) {
    companion object {
        private const val BUNDLED_DEFAULT_FONT = "fonts/newrodin.otf"
        private const val USER_FONT_PREFIX = "launcher_font"
        private const val LEGACY_FONT_NAME = "VSH-CustomFont"
    }

    private val userFontDir: File
        get() = File(vsh.filesDir, "fonts").apply { mkdirs() }

    fun initialize() {
        ensureButtonFont()
        reloadActiveFont()
    }

    fun getActiveFontName(): String {
        val userFont = getPersistedUserFontFile()
        if (userFont != null) return userFont.name

        val legacyFont = findLegacyFontFile()
        if (legacyFont != null) return legacyFont.name

        return vsh.getString(R.string.common_default)
    }

    fun importUserFont(uri: Uri): Result<File> = runCatching {
        ensureButtonFont()

        val displayName = queryDisplayName(uri) ?: uri.lastPathSegment ?: "custom_font.ttf"
        val mimeType = vsh.contentResolver.getType(uri)
        val extension = resolveExtension(displayName, mimeType)
            ?: throw IllegalArgumentException("Unsupported font file")

        val tempFile = File(vsh.cacheDir, "picked_font.$extension")
        val targetFile = File(userFontDir, "$USER_FONT_PREFIX.$extension")

        try {
            vsh.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: throw IllegalStateException("Unable to open selected font")

            // Verify the font before committing it as the active launcher font.
            Typeface.createFromFile(tempFile)

            userFontDir.listFiles()
                ?.filter { it.name.startsWith("$USER_FONT_PREFIX.") }
                ?.forEach { it.delete() }

            tempFile.copyTo(targetFile, overwrite = true)
            val loadedFont = Typeface.createFromFile(targetFile)
            FontCollections.masterFont = loadedFont
            vsh.M.pref.set(PrefEntry.USER_FONT_PATH, targetFile.absolutePath).push()
            targetFile
        } finally {
            if (tempFile.exists()) tempFile.delete()
        }
    }

    fun resetUserFont() {
        userFontDir.listFiles()
            ?.filter { it.name.startsWith("$USER_FONT_PREFIX.") }
            ?.forEach { it.delete() }
        vsh.M.pref.remove(PrefEntry.USER_FONT_PATH).push()
        reloadActiveFont()
    }

    fun reloadActiveFont() {
        ensureButtonFont()
        FontCollections.masterFont = resolveActiveTypeface()
    }

    private fun ensureButtonFont() {
        if (FontCollections.buttonFont === Typeface.DEFAULT) {
            FontCollections.buttonFont = Typeface.createFromAsset(vsh.assets, "vshbtn.ttf")
        }
    }

    private fun resolveActiveTypeface(): Typeface {
        val userFont = getPersistedUserFontFile()
        if (userFont != null) {
            val userTypeface = runCatching { Typeface.createFromFile(userFont) }.getOrNull()
            if (userTypeface != null) {
                return userTypeface
            } else {
                vsh.M.pref.remove(PrefEntry.USER_FONT_PATH).push()
            }
        }

        val legacyFont = findLegacyFontFile()
        if (legacyFont != null) {
            val legacyTypeface = runCatching { Typeface.createFromFile(legacyFont) }.getOrNull()
            if (legacyTypeface != null) {
                return legacyTypeface
            }
        }

        val bundledDefault = runCatching {
            Typeface.createFromAsset(vsh.assets, BUNDLED_DEFAULT_FONT)
        }.getOrNull()
        if (bundledDefault != null) {
            return bundledDefault
        }

        return Typeface.SANS_SERIF
    }

    private fun getPersistedUserFontFile(): File? {
        val path = vsh.M.pref.get(PrefEntry.USER_FONT_PATH, "")
        if (path.isBlank()) return null

        val fontFile = File(path)
        return if (fontFile.exists() && fontFile.isFile) {
            fontFile
        } else {
            vsh.M.pref.remove(PrefEntry.USER_FONT_PATH).push()
            null
        }
    }

    private fun findLegacyFontFile(): File? {
        return FileQuery(VshBaseDirs.FLASH_DATA_DIR)
            .atPath("font")
            .withNames(LEGACY_FONT_NAME)
            .withExtensions("ttf", "TTF", "otf", "OTF")
            .createParentDirectory(true)
            .execute(vsh)
            .firstOrNull { it.exists() && it.isFile }
    }

    private fun queryDisplayName(uri: Uri): String? {
        vsh.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) {
                    return cursor.getString(idx)
                }
            }
        }
        return null
    }

    private fun resolveExtension(displayName: String, mimeType: String?): String? {
        val lowerName = displayName.lowercase()
        return when {
            lowerName.endsWith(".ttf") -> "ttf"
            lowerName.endsWith(".otf") -> "otf"
            mimeType?.contains("ttf", ignoreCase = true) == true -> "ttf"
            mimeType?.contains("otf", ignoreCase = true) == true -> "otf"
            else -> null
        }
    }
}
