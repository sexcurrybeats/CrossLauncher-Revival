package id.psw.vshlauncher.submodules

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.webkit.MimeTypeMap
import id.psw.vshlauncher.PrefEntry
import id.psw.vshlauncher.R
import id.psw.vshlauncher.Vsh
import id.psw.vshlauncher.VshBaseDirs
import id.psw.vshlauncher.VshResName
import id.psw.vshlauncher.VshResTypes
import id.psw.vshlauncher.postNotification
import id.psw.vshlauncher.submodules.theme.PtfParser
import id.psw.vshlauncher.types.FileQuery
import id.psw.vshlauncher.types.IniFile
import id.psw.vshlauncher.types.XmbTheme
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class CustomizerPackageSubmodule(private val vsh: Vsh) : IVshSubmodule {
    data class ResetAllCustomizationsResult(
        val deletedEntries: Int
    )

    data class Parameters(
        var packageName:String,
        var name:String,
        var description:String,
        var artAuthor:String,
        var packageAuthor:String,
        var isGame:Boolean
    )

    private val _themes = mutableListOf<XmbTheme>()
    val themes: List<XmbTheme> get() = _themes

    private var _activeTheme : XmbTheme? = null
    val activeTheme: XmbTheme? get() = _activeTheme

    private var _activeThemeBackdrop : BitmapRef? = null
    val activeThemeBackdrop : BitmapRef? get() = _activeThemeBackdrop

    private var _activeThemeBackdropOverlay : BitmapRef? = null
    val activeThemeBackdropOverlay : BitmapRef? get() = _activeThemeBackdropOverlay

    private var _activeThemePortraitBackdrop : BitmapRef? = null
    val activeThemePortraitBackdrop : BitmapRef? get() = _activeThemePortraitBackdrop

    private var _activeThemePortraitBackdropOverlay : BitmapRef? = null
    val activeThemePortraitBackdropOverlay : BitmapRef? get() = _activeThemePortraitBackdropOverlay

    private var _activeLauncherWallpaper : BitmapRef? = null
    val activeLauncherWallpaper : BitmapRef? get() = _activeLauncherWallpaper

    companion object {
        const val MAX_COLDBOOT_AUDIO_DURATION_MS = 10_000L
        const val PACKAGE_PARAMETER_INFO = "PARAM.INI"
        val PACKAGE_STATIC_ICON_FILENAMES = arrayOf("ICON0.PNG","ICON0.JPG")
        val PACKAGE_ANIMATED_ICON_FILENAMES = arrayOf("ICON1.APNG","ICON1.WEBP","ICON1.GIF")
        val PACKAGE_BACKDROP_FILENAMES = arrayOf("PIC1.PNG","PIC1.JPG")
        val PACKAGE_OVERLAY_BACKDROP_FILENAMES = arrayOf("PIC0.PNG","PIC0.JPG")
        val PACKAGE_PORT_BACKDROP_FILENAMES = arrayOf("PIC1_P.PNG","PIC1_P.JPG")
        val PACKAGE_PORT_OVERLAY_BACKDROP_FILENAMES = arrayOf("PIC0_P.PNG","PIC0_P.JPG")
        val PACKAGE_BACK_SOUND_FILENAMES = arrayOf("SND0.AAC","SND0.MP3")
    }

    override fun onCreate() {
        refreshThemes()
        loadActiveTheme()
        loadActiveLauncherWallpaper()
    }

    override fun onDestroy() {
        _themes.clear()
        _activeThemeBackdrop?.release()
        _activeThemeBackdropOverlay?.release()
        _activeThemePortraitBackdrop?.release()
        _activeThemePortraitBackdropOverlay?.release()
        _activeLauncherWallpaper?.release()
    }

    fun loadActiveTheme() {
        val currentId = vsh.M.pref.get(PrefEntry.CURRENT_THEME_ID, "")
        _activeTheme = _themes.find { it.id == currentId }

        _activeThemeBackdrop?.release()
        _activeThemeBackdropOverlay?.release()
        _activeThemePortraitBackdrop?.release()
        _activeThemePortraitBackdropOverlay?.release()

        _activeTheme?.let { theme ->
            _activeThemeBackdrop = getThemeBitmap(theme.id, theme.backdropPath)
            _activeThemeBackdropOverlay = getThemeBitmap(theme.id, theme.backdropOverlayPath)
            _activeThemePortraitBackdrop = getThemeBitmap(theme.id, theme.backdropPortraitPath)
            _activeThemePortraitBackdropOverlay = getThemeBitmap(theme.id, theme.backdropPortraitOverlayPath)
        }
    }

    fun saveLauncherWallpaper(bitmap: Bitmap): Boolean {
        val wallpaperFile = File(vsh.filesDir, "wallpaper/launcher_wallpaper.png")
        return try {
            wallpaperFile.parentFile?.mkdirs()
            wallpaperFile.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            vsh.M.pref.set(PrefEntry.LAUNCHER_WALLPAPER_PATH, wallpaperFile.absolutePath).push()
            loadActiveLauncherWallpaper()
            true
        } catch (e: Exception) {
            vsh.postNotification(null, "Launcher wallpaper failed", e.toString(), 5.0f)
            false
        }
    }

    fun resetLauncherWallpaper(): Boolean {
        val path = vsh.M.pref.get(PrefEntry.LAUNCHER_WALLPAPER_PATH, "")
        vsh.M.pref.set(PrefEntry.LAUNCHER_WALLPAPER_PATH, "").push()
        _activeLauncherWallpaper?.release()
        _activeLauncherWallpaper = null

        if (path.isNotBlank()) {
            try {
                File(path).takeIf { it.exists() && it.parentFile == File(vsh.filesDir, "wallpaper") }?.delete()
            } catch (_: Exception) {
            }
        }
        return true
    }

    fun importColdbootImage(uri: Uri): Result<File> {
        return importColdbootResource(uri, VshResTypes.IMAGES, "png")
    }

    fun importColdbootAudio(uri: Uri): Result<File> {
        return importColdbootResource(uri, VshResTypes.SOUNDS, "mp3") {
            validateColdbootAudioUri(uri)
        }
    }

    fun importGamebootImage(uri: Uri): Result<File> {
        return importGamebootResource(uri, gamebootVisualExtensions(), "png")
    }

    fun importGamebootAudio(uri: Uri): Result<File> {
        return importGamebootResource(uri, VshResTypes.SOUNDS, "mp3")
    }

    fun importBatteryGlyph(uri: Uri): Result<File> {
        return runCatching {
            val extension = resolveImportExtension(
                uri,
                VshResTypes.ICONS,
                "png",
                vsh.getString(R.string.error_battery_glyph_unsupported_type)
            )
            deleteBatteryGlyphFiles()

            val target = FileQuery(VshBaseDirs.VSH_RESOURCES_DIR)
                .atPath("hud")
                .createParentDirectory(true)
                .withNames(VshResName.BATTERY_GLYPH.first())
                .withExtensions(extension)
                .execute(vsh)
                .firstOrNull()
                ?: throw IllegalStateException(vsh.getString(R.string.error_battery_glyph_destination_missing))

            target.parentFile?.mkdirs()
            vsh.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output, Vsh.COPY_DATA_SIZE_BUFFER)
                }
            } ?: throw IllegalStateException(vsh.getString(R.string.error_battery_glyph_source_missing))

            vsh.xmbView?.widgets?.statusBar?.reloadBatteryGlyphOverride()
            target
        }
    }

    fun resetColdbootOverrides(): Int {
        val deleted = deleteColdbootFiles(VshResTypes.IMAGES) + deleteColdbootFiles(VshResTypes.SOUNDS)
        vsh.xmbView?.screens?.coldBoot?.reloadResources()
        return deleted
    }

    fun resetGamebootOverrides(): Int {
        val deleted = deleteGamebootFiles(gamebootVisualExtensions()) + deleteGamebootFiles(VshResTypes.SOUNDS)
        vsh.xmbView?.screens?.gameBoot?.reloadResources()
        return deleted
    }

    fun resetBatteryGlyphOverride(): Int {
        val deleted = deleteBatteryGlyphFiles()
        vsh.xmbView?.widgets?.statusBar?.reloadBatteryGlyphOverride()
        return deleted
    }

    fun resetAllCustomizationsForFreshInstall(): ResetAllCustomizationsResult {
        val deletedEntries = listOf(
            externalChild(IconManager.CUSTOM_ICON_DIR),
            externalChild("themes"),
            externalChild(VshBaseDirs.VSH_RESOURCES_DIR),
            externalChild(VshBaseDirs.FLASH_DATA_DIR),
            externalChild(VshBaseDirs.APPS_DIR),
            externalChild(VshBaseDirs.SHORTCUTS_DIR),
            internalChild("fonts"),
            internalChild("wallpaper"),
            internalChild(VshBaseDirs.APPS_DIR),
            internalChild(VshBaseDirs.SHORTCUTS_DIR)
        ).sumOf { deleteAppOwnedTree(it) }

        _themes.clear()
        _activeTheme = null
        _activeThemeBackdrop?.release()
        _activeThemeBackdrop = null
        _activeThemeBackdropOverlay?.release()
        _activeThemeBackdropOverlay = null
        _activeThemePortraitBackdrop?.release()
        _activeThemePortraitBackdrop = null
        _activeThemePortraitBackdropOverlay?.release()
        _activeThemePortraitBackdropOverlay = null
        _activeLauncherWallpaper?.release()
        _activeLauncherWallpaper = null

        return ResetAllCustomizationsResult(deletedEntries)
    }

    private fun importColdbootResource(
        uri: Uri,
        allowedExtensions: Array<String>,
        fallbackExtension: String,
        validateSource: (() -> Unit)? = null
    ): Result<File> {
        return runCatching {
            val extension = resolveImportExtension(uri, allowedExtensions, fallbackExtension)
            validateSource?.invoke()
            deleteColdbootFiles(allowedExtensions)

            val target = FileQuery(VshBaseDirs.VSH_RESOURCES_DIR)
                .createParentDirectory(true)
                .withNames(VshResName.COLDBOOT.first())
                .withExtensions(extension)
                .execute(vsh)
                .firstOrNull()
                ?: throw IllegalStateException(vsh.getString(R.string.error_coldboot_destination_missing))

            target.parentFile?.mkdirs()
            vsh.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output, Vsh.COPY_DATA_SIZE_BUFFER)
                }
            } ?: throw IllegalStateException(vsh.getString(R.string.error_coldboot_source_missing))

            vsh.xmbView?.screens?.coldBoot?.reloadResources()
            target
        }
    }

    private fun importGamebootResource(
        uri: Uri,
        allowedExtensions: Array<String>,
        fallbackExtension: String
    ): Result<File> {
        return runCatching {
            val extension = resolveImportExtension(
                uri,
                allowedExtensions,
                fallbackExtension,
                vsh.getString(R.string.error_gameboot_unsupported_type)
            )
            if (allowedExtensions.any { candidate -> VshResTypes.ANIMATED_ICONS.any { it.equals(candidate, ignoreCase = true) } }) {
                deleteGamebootFiles(gamebootVisualExtensions())
            } else {
                deleteGamebootFiles(allowedExtensions)
            }

            val target = FileQuery(VshBaseDirs.VSH_RESOURCES_DIR)
                .createParentDirectory(true)
                .withNames(VshResName.GAMEBOOT.first())
                .withExtensions(extension)
                .execute(vsh)
                .firstOrNull()
                ?: throw IllegalStateException(vsh.getString(R.string.error_gameboot_destination_missing))

            target.parentFile?.mkdirs()
            vsh.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output, Vsh.COPY_DATA_SIZE_BUFFER)
                }
            } ?: throw IllegalStateException(vsh.getString(R.string.error_gameboot_source_missing))

            vsh.xmbView?.screens?.gameBoot?.reloadResources()
            target
        }
    }

    private fun validateColdbootAudioUri(uri: Uri) {
        val retriever = MediaMetadataRetriever()
        try {
            vsh.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                if (afd.length >= 0) {
                    retriever.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                } else {
                    retriever.setDataSource(afd.fileDescriptor)
                }
            } ?: throw IllegalStateException(vsh.getString(R.string.error_coldboot_source_missing))

            validateColdbootAudioDuration(retriever)
        } finally {
            retriever.release()
        }
    }

    fun validateColdbootAudioFile(file: File) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            validateColdbootAudioDuration(retriever)
        } finally {
            retriever.release()
        }
    }

    private fun validateColdbootAudioDuration(retriever: MediaMetadataRetriever) {
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull()
            ?: throw IllegalArgumentException(vsh.getString(R.string.error_coldboot_audio_duration_unknown))

        if (durationMs > MAX_COLDBOOT_AUDIO_DURATION_MS) {
            throw IllegalArgumentException(vsh.getString(R.string.error_coldboot_audio_too_long))
        }
    }

    private fun deleteColdbootFiles(extensions: Array<String>): Int {
        return FileQuery(VshBaseDirs.VSH_RESOURCES_DIR)
            .withNames(*VshResName.COLDBOOT)
            .withExtensionArray(extensions)
            .onlyIncludeExists(true)
            .execute(vsh)
            .count { file ->
                file.isFile && runCatching { file.delete() }.getOrDefault(false)
            }
    }

    private fun deleteGamebootFiles(extensions: Array<String>): Int {
        return FileQuery(VshBaseDirs.VSH_RESOURCES_DIR)
            .withNames(*VshResName.GAMEBOOT)
            .withExtensionArray(extensions)
            .onlyIncludeExists(true)
            .execute(vsh)
            .count { file ->
                file.isFile && runCatching { file.delete() }.getOrDefault(false)
            }
    }

    private fun deleteBatteryGlyphFiles(): Int {
        return FileQuery(VshBaseDirs.VSH_RESOURCES_DIR)
            .atPath("hud")
            .withNames(*VshResName.BATTERY_GLYPH)
            .withExtensionArray(VshResTypes.ICONS)
            .onlyIncludeExists(true)
            .execute(vsh)
            .count { file ->
                file.isFile && runCatching { file.delete() }.getOrDefault(false)
            }
    }

    private fun externalChild(relativePath: String): File? {
        val root = vsh.getExternalFilesDir(null) ?: return null
        return File(root, relativePath)
    }

    private fun internalChild(relativePath: String): File = File(vsh.filesDir, relativePath)

    private fun deleteAppOwnedTree(target: File?): Int {
        if (target == null || !target.exists()) return 0

        val allowedRoots = listOfNotNull(vsh.getExternalFilesDir(null), vsh.filesDir)
            .map { it.canonicalFile }
        val canonicalTarget = target.canonicalFile
        val isSafeTarget = allowedRoots.any { root ->
            canonicalTarget.path != root.path && canonicalTarget.path.startsWith(root.path + File.separator)
        }
        if (!isSafeTarget) {
            throw IllegalStateException("Refusing to reset unsafe path: ${target.absolutePath}")
        }

        val count = if (canonicalTarget.isDirectory) {
            canonicalTarget.walkBottomUp().count()
        } else {
            1
        }
        if (!canonicalTarget.deleteRecursively()) {
            throw IllegalStateException("Failed to delete ${target.absolutePath}")
        }
        return count
    }

    private fun gamebootVisualExtensions(): Array<String> {
        return (VshResTypes.IMAGES + VshResTypes.ANIMATED_ICONS)
            .distinctBy { it.lowercase() }
            .toTypedArray()
    }

    private fun resolveImportExtension(
        uri: Uri,
        allowedExtensions: Array<String>,
        fallbackExtension: String,
        unsupportedMessage: String = vsh.getString(R.string.error_coldboot_unsupported_type)
    ): String {
        val candidates = arrayListOf<String>()

        uri.lastPathSegment
            ?.substringAfterLast('.', "")
            ?.takeIf { it.isNotBlank() }
            ?.let { candidates.add(it) }

        vsh.contentResolver.getType(uri)
            ?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
            ?.takeIf { it.isNotBlank() }
            ?.let { candidates.add(it) }

        val supported = candidates
            .map { normalizeExtension(it) }
            .firstOrNull { candidate -> allowedExtensions.any { it.equals(candidate, ignoreCase = true) } }

        if (supported != null) return supported
        if (candidates.isEmpty()) return fallbackExtension
        throw IllegalArgumentException(unsupportedMessage)
    }

    private fun normalizeExtension(extension: String): String {
        return when (extension.lowercase()) {
            "jpe" -> "jpg"
            "mpeg", "mpga" -> "mp3"
            "x-wav" -> "wav"
            else -> extension.lowercase()
        }
    }

    fun loadActiveLauncherWallpaper() {
        _activeLauncherWallpaper?.release()
        _activeLauncherWallpaper = null

        val path = vsh.M.pref.get(PrefEntry.LAUNCHER_WALLPAPER_PATH, "")
        if (path.isBlank()) return

        val file = File(path)
        if (!file.exists()) return

        _activeLauncherWallpaper = BitmapRef("launcher_wallpaper://${file.absolutePath}", {
            BitmapFactory.decodeFile(file.absolutePath)
        })
    }

    fun refreshThemes() {
        _themes.clear()
        val themeDir = File(vsh.getExternalFilesDir(null), "themes")
        if (!themeDir.exists()) themeDir.mkdirs()

        themeDir.listFiles { _, name -> name.endsWith(".xtf", true) || name.endsWith(".zip", true) }?.forEach { file ->
            val theme = readThemeFromPackage(file)
            if (theme != null) {
                _themes.add(theme)
            }
        }

        // PTF Themes
        val ptfParser = PtfParser(vsh)
        themeDir.listFiles { _, name -> name.endsWith(".ptf", true) }?.forEach { file ->
            val theme = ptfParser.parse(file)
            if (theme != null) {
                theme.id = file.absolutePath
                _themes.add(theme)
            }
        }
    }

    private fun readThemeFromPackage(file: File): XmbTheme? {
        val theme = XmbTheme(id = file.absolutePath)
        var hasParam = false
        try {
            ZipInputStream(file.inputStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val entryName = entry.name.uppercase()
                    if (entryName == PACKAGE_PARAMETER_INFO && !entry.isDirectory) {
                        val content = zip.bufferedReader(Charsets.UTF_8).readText()
                        val ini = IniFile()
                        ini.parse(content)

                        theme.name = ini.get("TITLE", "TITLE", "Unnamed Theme") { it }
                        theme.description = ini.get("TITLE", "DESCRIPTION", "") { it }
                        theme.author = ini.get("TITLE", "AUTHOR", "Unknown") { it }

                        // Wave settings from INI
                        theme.waveStyle = ini.get("WAVE", "STYLE", 0.toByte()) { it.toByteOrNull() ?: 0 }
                        theme.waveSpeed = ini.get("WAVE", "SPEED", 1.0f) { it.toFloatOrNull() ?: 1.0f }
                        theme.waveMonth = ini.get("WAVE", "MONTH", 0) { it.toIntOrNull() ?: 0 }
                        theme.waveDayNight = ini.get("WAVE", "DAYNIGHT", true) { it.toIntOrNull() != 0 }

                        hasParam = true
                    }

                    // Check for asset existence
                    when (entryName) {
                        in PACKAGE_STATIC_ICON_FILENAMES -> theme.icon0Path = entry.name
                        in PACKAGE_BACKDROP_FILENAMES -> theme.backdropPath = entry.name
                        in PACKAGE_OVERLAY_BACKDROP_FILENAMES -> theme.backdropOverlayPath = entry.name
                        in PACKAGE_BACK_SOUND_FILENAMES -> theme.snd0Path = entry.name
                        in PACKAGE_PORT_BACKDROP_FILENAMES -> theme.backdropPortraitPath = entry.name
                        in PACKAGE_PORT_OVERLAY_BACKDROP_FILENAMES -> theme.backdropPortraitOverlayPath = entry.name
                    }

                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } catch (e: IOException) {
            return null
        }
        return if (hasParam) theme else null
    }

    fun getThemeBitmap(themeId: String, assetPath: String?): BitmapRef? {
        if (assetPath == null || themeId.isEmpty()) return null
        return BitmapRef("theme://$themeId/$assetPath", {
            try {
                ZipInputStream(File(themeId).inputStream()).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (entry.name == assetPath) {
                            return@BitmapRef BitmapFactory.decodeStream(zip)
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            null
        })
    }
}
