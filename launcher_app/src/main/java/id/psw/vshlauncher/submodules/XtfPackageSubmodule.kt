package id.psw.vshlauncher.submodules

import android.content.Intent
import android.net.Uri
import id.psw.vshlauncher.BuildConfig
import id.psw.vshlauncher.PrefEntry
import id.psw.vshlauncher.R
import id.psw.vshlauncher.Vsh
import id.psw.vshlauncher.VshBaseDirs
import id.psw.vshlauncher.VshResName
import id.psw.vshlauncher.VshResTypes
import id.psw.vshlauncher.postNotification
import id.psw.vshlauncher.types.FileQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class XtfPackageSubmodule(private val vsh: Vsh) : IVshSubmodule {
    companion object {
        private const val FORMAT_VERSION = 1
        private const val PACKAGE_TYPE = "crosslauncher.shell-state"
        private const val WAVE_PREF_NAME = "libwave_setting"
        private const val THEME_DIR = "themes"
        private const val USER_FONT_DIR = "fonts"
        private const val CUSTOM_ROOT = "custom"
        private const val COLDBOOT_SECTION = "coldboot_assets"
        private const val GAMEBOOT_SECTION = "gameboot_assets"
        private const val MENU_SFX_SECTION = "menu_sfx"
        private const val BATTERY_GLYPH_SECTION = "battery_glyph"
        private const val MENU_SFX_DIR = "sfx"
        private const val HUD_DIR = "hud"
        private val MENU_SFX_EXTENSIONS = arrayOf("ogg", "wav", "mp3")
    }

    private enum class ColdbootResourceType {
        Image,
        Audio
    }

    private enum class GamebootResourceType {
        Image,
        Audio
    }

    private enum class MenuSfxResourceType(val resourceName: String) {
        Selection("select"),
        Confirm("confirm"),
        Cancel("cancel")
    }

    override fun onCreate() = Unit
    override fun onDestroy() = Unit

    fun createExportIntent(): Intent {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        return Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            // Keep the portable package extension as .xtf; some document providers
            // append ".zip" automatically when the MIME type is application/zip.
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_TITLE, "CrossLauncher_Config_$stamp.xtf")
        }
    }

    fun createImportIntent(): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
    }

    fun exportCurrentToUri(uri: Uri) {
        vsh.lifeScope.launch(Dispatchers.IO) {
            runCatching {
                vsh.M.pref.flushNow()
                vsh.getSharedPreferences(WAVE_PREF_NAME, android.content.Context.MODE_PRIVATE)
                    .edit()
                    .commit()

                vsh.contentResolver.openOutputStream(uri)?.use { output ->
                    ZipOutputStream(output.buffered()).use { zip ->
                        val included = linkedSetOf<String>()
                        addText(zip, "PARAM.INI", createParamIni())

                        addSanitizedLauncherPrefsIfPresent(zip, included)
                        addSharedPrefsIfPresent(zip, WAVE_PREF_NAME, "prefs/libwave_setting.xml", included)
                        addDirectoryIfPresent(zip, appExternalFile(IconManager.CUSTOM_ICON_DIR), "$CUSTOM_ROOT/${IconManager.CUSTOM_ICON_DIR}", "custom_icons", included)
                        addColdbootOverridesIfPresent(zip, included)
                        addGamebootOverridesIfPresent(zip, included)
                        addMenuSfxOverridesIfPresent(zip, included)
                        addBatteryGlyphOverrideIfPresent(zip, included)

                        val currentTheme = vsh.M.pref.get(PrefEntry.CURRENT_THEME_ID, "")
                        val userFontPath = vsh.M.pref.get(PrefEntry.USER_FONT_PATH, "")
                        addText(zip, "refs/current_theme.txt", currentTheme)
                        addText(zip, "refs/user_font_path.txt", userFontPath)
                        included.add("refs")

                        addFontIfPresent(zip, userFontPath, included)
                        addThemeIfPresent(zip, currentTheme, included)

                        addText(zip, "manifest.json", createManifest(included))
                    }
                } ?: throw IllegalStateException("Unable to open export destination")
            }.onSuccess {
                withContext(Dispatchers.Main) {
                    vsh.postNotification(R.drawable.category_setting, "XTF Exported", "Current launcher state was saved")
                }
            }.onFailure { error ->
                withContext(Dispatchers.Main) {
                    vsh.postNotification(
                        R.drawable.ic_error,
                        "XTF Export Failed",
                        error.localizedMessage ?: error.javaClass.simpleName,
                        5.0f
                    )
                }
            }
        }
    }

    fun importFromUri(uri: Uri) {
        vsh.lifeScope.launch(Dispatchers.IO) {
            runCatching {
                val importedRefs = ImportedRefs()
                val clearedPrefixes = mutableSetOf<String>()

                vsh.contentResolver.openInputStream(uri)?.use { input ->
                    ZipInputStream(input.buffered()).use { zip ->
                        var entry = zip.nextEntry
                        while (entry != null) {
                            if (!entry.isDirectory) {
                                importEntry(zip, entry.name, importedRefs, clearedPrefixes)
                            }
                            zip.closeEntry()
                            entry = zip.nextEntry
                        }
                    }
                } ?: throw IllegalStateException("Unable to open XTF package")

                rewriteImportedRefs(importedRefs)
            }.onSuccess {
                withContext(Dispatchers.Main) {
                    vsh.M.font.reloadActiveFont()
                    vsh.M.customizer.refreshThemes()
                    vsh.M.customizer.loadActiveTheme()
                    vsh.xmbView?.screens?.coldBoot?.reloadResources()
                    vsh.xmbView?.screens?.gameBoot?.reloadResources()
                    vsh.xmbView?.widgets?.statusBar?.reloadBatteryGlyphOverride()
                    vsh.M.audio.loadSfxData(false)
                    vsh.M.bmp.releaseAll()
                    vsh.M.apps.reloadAppList()
                    vsh.postNotification(
                        R.drawable.category_setting,
                        "XTF Imported",
                        "Restarting launcher to apply restored settings",
                        5.0f
                    )
                    delay(750)
                    vsh.restart()
                }
            }.onFailure { error ->
                withContext(Dispatchers.Main) {
                    vsh.postNotification(
                        R.drawable.ic_error,
                        "XTF Import Failed",
                        error.localizedMessage ?: error.javaClass.simpleName,
                        5.0f
                    )
                }
            }
        }
    }

    private fun addSharedPrefsIfPresent(
        zip: ZipOutputStream,
        prefName: String,
        zipPath: String,
        included: MutableSet<String>
    ) {
        val file = sharedPrefsFile(prefName)
        if (file.exists() && file.isFile) {
            addFile(zip, file, zipPath)
            included.add("prefs:$prefName")
        }
    }

    private fun addSanitizedLauncherPrefsIfPresent(
        zip: ZipOutputStream,
        included: MutableSet<String>
    ) {
        val file = sharedPrefsFile(PreferenceSubmodule.PREF_NAME)
        if (!file.exists() || !file.isFile) return

        var xml = file.readText(Charsets.UTF_8)
        val exportableLaunchItems = vsh.serializeCustomLaunchItemRecords(
            vsh.getExportableCustomLaunchItemRecords()
        )
        xml = writeSharedPrefString(xml, PrefEntry.CUSTOM_LAUNCH_ITEMS, exportableLaunchItems)
        addText(zip, "prefs/xRegistry.sys.xml", xml)
        included.add("prefs:${PreferenceSubmodule.PREF_NAME}")
    }

    private fun addDirectoryIfPresent(
        zip: ZipOutputStream,
        dir: File,
        zipPrefix: String,
        section: String,
        included: MutableSet<String>
    ) {
        if (!dir.exists() || !dir.isDirectory) return
        dir.walkTopDown()
            .filter { it.isFile }
            .forEach { file ->
                val rel = file.relativeTo(dir).invariantSeparatorsPath
                addFile(zip, file, "$zipPrefix/$rel")
                included.add(section)
            }
    }

    private fun addFontIfPresent(zip: ZipOutputStream, path: String, included: MutableSet<String>) {
        val font = File(path)
        if (font.exists() && font.isFile && isAppOwned(font)) {
            val ext = font.extension.ifBlank { "ttf" }
            addFile(zip, font, "fonts/active_font.$ext")
            included.add("font")
        }
    }

    private fun addThemeIfPresent(zip: ZipOutputStream, path: String, included: MutableSet<String>) {
        val theme = File(path)
        if (theme.exists() && theme.isFile && isAppOwned(theme)) {
            val ext = theme.extension.ifBlank { "xtf" }
            addFile(zip, theme, "themes/active_theme.$ext")
            included.add("theme")
        }
    }

    private fun addColdbootOverridesIfPresent(zip: ZipOutputStream, included: MutableSet<String>) {
        coldbootOverrideFiles().forEach { file ->
            addFile(zip, file, "$CUSTOM_ROOT/${VshBaseDirs.VSH_RESOURCES_DIR}/${file.name}")
            included.add(COLDBOOT_SECTION)
        }
    }

    private fun addGamebootOverridesIfPresent(zip: ZipOutputStream, included: MutableSet<String>) {
        gamebootOverrideFiles().forEach { file ->
            addFile(zip, file, "$CUSTOM_ROOT/${VshBaseDirs.VSH_RESOURCES_DIR}/${file.name}")
            included.add(GAMEBOOT_SECTION)
        }
    }

    private fun addMenuSfxOverridesIfPresent(zip: ZipOutputStream, included: MutableSet<String>) {
        menuSfxOverrideFiles().forEach { file ->
            addFile(zip, file, "$CUSTOM_ROOT/${VshBaseDirs.VSH_RESOURCES_DIR}/$MENU_SFX_DIR/${file.name}")
            included.add(MENU_SFX_SECTION)
        }
    }

    private fun addBatteryGlyphOverrideIfPresent(zip: ZipOutputStream, included: MutableSet<String>) {
        batteryGlyphFiles().forEach { file ->
            addFile(zip, file, "$CUSTOM_ROOT/${VshBaseDirs.VSH_RESOURCES_DIR}/$HUD_DIR/${file.name}")
            included.add(BATTERY_GLYPH_SECTION)
        }
    }

    private fun coldbootOverrideFiles(): List<File> {
        return (coldbootFiles(VshResTypes.IMAGES) + coldbootFiles(VshResTypes.SOUNDS))
            .distinctBy { file ->
                runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)
            }
    }

    private fun gamebootOverrideFiles(): List<File> {
        return (gamebootFiles(gamebootExtensions(GamebootResourceType.Image)) + gamebootFiles(VshResTypes.SOUNDS))
            .distinctBy { file ->
                runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)
            }
    }

    private fun menuSfxOverrideFiles(): List<File> {
        return MenuSfxResourceType.values()
            .flatMap { type -> menuSfxFiles(type) }
            .distinctBy { file ->
                runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)
            }
    }

    private fun batteryGlyphFiles(): List<File> {
        return FileQuery(VshBaseDirs.VSH_RESOURCES_DIR)
            .atPath(HUD_DIR)
            .withNames(*VshResName.BATTERY_GLYPH)
            .withExtensionArray(VshResTypes.ICONS)
            .onlyIncludeExists(true)
            .execute(vsh)
            .filter { it.isFile }
            .distinctBy { file ->
                runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)
            }
    }

    private fun coldbootFiles(extensions: Array<String>): List<File> {
        return FileQuery(VshBaseDirs.VSH_RESOURCES_DIR)
            .withNames(*VshResName.COLDBOOT)
            .withExtensionArray(extensions)
            .onlyIncludeExists(true)
            .execute(vsh)
            .filter { it.isFile }
    }

    private fun gamebootFiles(extensions: Array<String>): List<File> {
        return FileQuery(VshBaseDirs.VSH_RESOURCES_DIR)
            .withNames(*VshResName.GAMEBOOT)
            .withExtensionArray(extensions)
            .onlyIncludeExists(true)
            .execute(vsh)
            .filter { it.isFile }
    }

    private fun menuSfxFiles(type: MenuSfxResourceType): List<File> {
        return FileQuery(VshBaseDirs.VSH_RESOURCES_DIR)
            .atPath(MENU_SFX_DIR)
            .withNames(type.resourceName)
            .withExtensionArray(MENU_SFX_EXTENSIONS)
            .onlyIncludeExists(true)
            .execute(vsh)
            .filter { it.isFile }
    }

    private fun addText(zip: ZipOutputStream, path: String, text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        zip.putNextEntry(ZipEntry(path))
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun addFile(zip: ZipOutputStream, file: File, path: String) {
        zip.putNextEntry(ZipEntry(path))
        file.inputStream().use { it.copyTo(zip, Vsh.COPY_DATA_SIZE_BUFFER) }
        zip.closeEntry()
    }

    private fun importEntry(
        zip: ZipInputStream,
        entryName: String,
        importedRefs: ImportedRefs,
        clearedPrefixes: MutableSet<String>
    ) {
        when {
            entryName == "prefs/xRegistry.sys.xml" -> copyZipEntryToFile(zip, sharedPrefsFile(PreferenceSubmodule.PREF_NAME))
            entryName == "prefs/libwave_setting.xml" -> copyZipEntryToFile(zip, sharedPrefsFile(WAVE_PREF_NAME))
            entryName.startsWith("custom/${VshBaseDirs.APPS_DIR}/") -> {
                val destRoot = appExternalFile(VshBaseDirs.APPS_DIR)
                clearDestinationOnce(destRoot, "apps", clearedPrefixes)
                copyZipEntryToSafeChild(zip, destRoot, entryName.removePrefix("custom/${VshBaseDirs.APPS_DIR}/"))
            }
            entryName.startsWith("custom/${IconManager.CUSTOM_ICON_DIR}/") -> {
                val destRoot = appExternalFile(IconManager.CUSTOM_ICON_DIR)
                clearDestinationOnce(destRoot, "icons", clearedPrefixes)
                copyZipEntryToSafeChild(zip, destRoot, entryName.removePrefix("custom/${IconManager.CUSTOM_ICON_DIR}/"))
            }
            entryName.startsWith("$CUSTOM_ROOT/${VshBaseDirs.VSH_RESOURCES_DIR}/") -> {
                val relativePath = entryName.removePrefix("$CUSTOM_ROOT/${VshBaseDirs.VSH_RESOURCES_DIR}/")
                val resourceType = coldbootResourceType(relativePath)
                val destRoot = appExternalFile(VshBaseDirs.VSH_RESOURCES_DIR)
                when (resourceType) {
                    ColdbootResourceType.Audio -> {
                        val tempFile = File(vsh.cacheDir, "xtf_coldboot_audio_${System.nanoTime()}.${relativePath.substringAfterLast('.', "tmp")}")
                        try {
                            copyZipEntryToFile(zip, tempFile)
                            vsh.M.customizer.validateColdbootAudioFile(tempFile)
                            clearColdbootTypeOnce(resourceType, clearedPrefixes)
                            copyFileToSafeChild(tempFile, destRoot, relativePath)
                        } finally {
                            runCatching { tempFile.delete() }
                        }
                    }
                    ColdbootResourceType.Image -> {
                        clearColdbootTypeOnce(resourceType, clearedPrefixes)
                        copyZipEntryToSafeChild(zip, destRoot, relativePath)
                    }
                    null -> {
                        val gamebootType = gamebootResourceType(relativePath)
                        if (gamebootType != null) {
                            clearGamebootTypeOnce(gamebootType, clearedPrefixes)
                            copyZipEntryToSafeChild(zip, destRoot, relativePath)
                            return
                        }
                        if (isBatteryGlyphResource(relativePath)) {
                            clearBatteryGlyphOnce(clearedPrefixes)
                            copyZipEntryToSafeChild(zip, destRoot, relativePath)
                            return
                        }
                        val menuSfxType = menuSfxResourceType(relativePath) ?: return
                        clearMenuSfxTypeOnce(menuSfxType, clearedPrefixes)
                        copyZipEntryToSafeChild(zip, destRoot, relativePath)
                    }
                }
            }
            entryName.startsWith("fonts/active_font.") -> {
                val ext = entryName.substringAfterLast('.', "ttf")
                val fontFile = File(vsh.filesDir, "$USER_FONT_DIR/launcher_font.$ext")
                copyZipEntryToFile(zip, fontFile)
                importedRefs.fontPath = fontFile.absolutePath
            }
            entryName.startsWith("themes/active_theme.") -> {
                val ext = entryName.substringAfterLast('.', "xtf")
                val themeFile = appExternalFile("$THEME_DIR/imported_active_theme.$ext")
                copyZipEntryToFile(zip, themeFile)
                importedRefs.themePath = themeFile.absolutePath
            }
        }
    }

    private fun clearDestinationOnce(destRoot: File, key: String, clearedPrefixes: MutableSet<String>) {
        if (!clearedPrefixes.add(key)) return
        if (!isInside(appExternalFile(""), destRoot)) {
            throw IllegalStateException("Refusing to clear unsafe path: ${destRoot.absolutePath}")
        }
        if (destRoot.exists()) destRoot.deleteRecursively()
        destRoot.mkdirs()
    }

    private fun clearColdbootTypeOnce(type: ColdbootResourceType, clearedPrefixes: MutableSet<String>) {
        if (!clearedPrefixes.add("coldboot:${type.name}")) return
        coldbootFiles(coldbootExtensions(type)).forEach { file ->
            runCatching { file.delete() }
        }
    }

    private fun clearMenuSfxTypeOnce(type: MenuSfxResourceType, clearedPrefixes: MutableSet<String>) {
        if (!clearedPrefixes.add("menuSfx:${type.resourceName}")) return
        menuSfxFiles(type).forEach { file ->
            runCatching { file.delete() }
        }
    }

    private fun clearGamebootTypeOnce(type: GamebootResourceType, clearedPrefixes: MutableSet<String>) {
        if (!clearedPrefixes.add("gameboot:${type.name}")) return
        gamebootFiles(gamebootExtensions(type)).forEach { file ->
            runCatching { file.delete() }
        }
    }

    private fun clearBatteryGlyphOnce(clearedPrefixes: MutableSet<String>) {
        if (!clearedPrefixes.add(BATTERY_GLYPH_SECTION)) return
        batteryGlyphFiles().forEach { file ->
            runCatching { file.delete() }
        }
    }

    private fun copyZipEntryToSafeChild(zip: ZipInputStream, destRoot: File, relativePath: String) {
        val outFile = File(destRoot, relativePath)
        if (!isInside(destRoot, outFile)) {
            throw IllegalArgumentException("Unsafe XTF path: $relativePath")
        }
        copyZipEntryToFile(zip, outFile)
    }

    private fun copyFileToSafeChild(source: File, destRoot: File, relativePath: String) {
        val outFile = File(destRoot, relativePath)
        if (!isInside(destRoot, outFile)) {
            throw IllegalArgumentException("Unsafe XTF path: $relativePath")
        }
        outFile.parentFile?.mkdirs()
        source.copyTo(outFile, overwrite = true)
    }

    private fun copyZipEntryToFile(zip: ZipInputStream, outFile: File) {
        outFile.parentFile?.mkdirs()
        outFile.outputStream().use { output ->
            zip.copyTo(output, Vsh.COPY_DATA_SIZE_BUFFER)
        }
    }

    private fun rewriteImportedRefs(importedRefs: ImportedRefs) {
        val prefFile = sharedPrefsFile(PreferenceSubmodule.PREF_NAME)
        if (!prefFile.exists()) return

        var xml = prefFile.readText(Charsets.UTF_8)
        importedRefs.fontPath?.let {
            xml = writeSharedPrefString(xml, PrefEntry.USER_FONT_PATH, it)
        }
        importedRefs.themePath?.let {
            xml = writeSharedPrefString(xml, PrefEntry.CURRENT_THEME_ID, it)
        }
        prefFile.writeText(xml, Charsets.UTF_8)
    }

    private fun writeSharedPrefString(xml: String, key: String, value: String): String {
        val escapedKey = Regex.escape(key)
        val replacement = """<string name="${escapeXml(key)}">${escapeXml(value)}</string>"""
        val existing = Regex("""<string\s+name="$escapedKey">.*?</string>""", RegexOption.DOT_MATCHES_ALL)
        if (existing.containsMatchIn(xml)) {
            return existing.replace(xml) { replacement }
        }
        val close = "</map>"
        return if (xml.contains(close)) {
            xml.replace(close, "    $replacement\n$close")
        } else if (Regex("""<map\s*/>""").containsMatchIn(xml)) {
            Regex("""<map\s*/>""").replace(xml) {
                "<map>\n    $replacement\n</map>"
            }
        } else {
            """<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    $replacement
</map>
"""
        }
    }

    private fun coldbootResourceType(fileName: String): ColdbootResourceType? {
        if (fileName.contains('/') || fileName.contains('\\')) return null

        val baseName = fileName.substringBeforeLast('.', "")
        val extension = fileName.substringAfterLast('.', "").takeIf { it.isNotBlank() } ?: return null

        if (VshResName.COLDBOOT.none { it == baseName }) return null

        return when {
            VshResTypes.IMAGES.any { it.equals(extension, ignoreCase = true) } -> ColdbootResourceType.Image
            VshResTypes.SOUNDS.any { it.equals(extension, ignoreCase = true) } -> ColdbootResourceType.Audio
            else -> null
        }
    }

    private fun menuSfxResourceType(relativePath: String): MenuSfxResourceType? {
        val normalizedPath = relativePath.replace('\\', '/')
        if (!normalizedPath.startsWith("$MENU_SFX_DIR/")) return null

        val fileName = normalizedPath.removePrefix("$MENU_SFX_DIR/")
        if (fileName.contains('/')) return null

        val baseName = fileName.substringBeforeLast('.', "")
        val extension = fileName.substringAfterLast('.', "").takeIf { it.isNotBlank() } ?: return null
        if (MENU_SFX_EXTENSIONS.none { it.equals(extension, ignoreCase = true) }) return null

        return MenuSfxResourceType.values()
            .firstOrNull { it.resourceName == baseName }
    }

    private fun isBatteryGlyphResource(relativePath: String): Boolean {
        val normalizedPath = relativePath.replace('\\', '/')
        if (!normalizedPath.startsWith("$HUD_DIR/")) return false

        val fileName = normalizedPath.removePrefix("$HUD_DIR/")
        if (fileName.contains('/')) return false

        val baseName = fileName.substringBeforeLast('.', "")
        val extension = fileName.substringAfterLast('.', "").takeIf { it.isNotBlank() } ?: return false
        return VshResName.BATTERY_GLYPH.any { it == baseName } &&
            VshResTypes.ICONS.any { it.equals(extension, ignoreCase = true) }
    }

    private fun gamebootResourceType(fileName: String): GamebootResourceType? {
        if (fileName.contains('/') || fileName.contains('\\')) return null

        val baseName = fileName.substringBeforeLast('.', "")
        val extension = fileName.substringAfterLast('.', "").takeIf { it.isNotBlank() } ?: return null

        if (VshResName.GAMEBOOT.none { it == baseName }) return null

        return when {
            gamebootExtensions(GamebootResourceType.Image).any { it.equals(extension, ignoreCase = true) } -> GamebootResourceType.Image
            VshResTypes.SOUNDS.any { it.equals(extension, ignoreCase = true) } -> GamebootResourceType.Audio
            else -> null
        }
    }

    private fun coldbootExtensions(type: ColdbootResourceType): Array<String> {
        return when (type) {
            ColdbootResourceType.Image -> VshResTypes.IMAGES
            ColdbootResourceType.Audio -> VshResTypes.SOUNDS
        }
    }

    private fun gamebootExtensions(type: GamebootResourceType): Array<String> {
        return when (type) {
            GamebootResourceType.Image -> (VshResTypes.IMAGES + VshResTypes.ANIMATED_ICONS)
                .distinctBy { it.lowercase() }
                .toTypedArray()
            GamebootResourceType.Audio -> VshResTypes.SOUNDS
        }
    }

    private fun createParamIni(): String {
        return """
[XTF]
TYPE=$PACKAGE_TYPE
VERSION=$FORMAT_VERSION
TITLE=CrossLauncher Config
AUTHOR=CrossLauncher

""".trimStart()
    }

    private fun createManifest(included: Set<String>): String {
        val includedJson = included.joinToString(",") { "\"${jsonEscape(it)}\"" }
        val created = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).format(Date())
        return """
{
  "packageType": "$PACKAGE_TYPE",
  "formatVersion": $FORMAT_VERSION,
  "title": "CrossLauncher Config",
  "author": "CrossLauncher",
  "created": "$created",
  "includedSections": [$includedJson],
  "appVersion": "${jsonEscape(BuildConfig.VERSION_NAME)}",
  "launcherVersion": ${BuildConfig.VERSION_CODE},
  "compatibility": {
    "portableRefs": true,
    "zipContainer": true,
    "coldbootAssets": true,
    "gamebootAssets": true,
    "menuSfxAssets": true,
    "batteryGlyphAsset": true,
    "prefRestartRecommended": true
  }
}
""".trimStart()
    }

    private fun sharedPrefsFile(prefName: String): File {
        return File(vsh.applicationInfo.dataDir, "shared_prefs/$prefName.xml")
    }

    private fun appExternalFile(path: String): File {
        return File(vsh.getExternalFilesDir(null), path)
    }

    private fun isAppOwned(file: File): Boolean {
        return isInside(vsh.filesDir, file) ||
            isInside(vsh.cacheDir, file) ||
            isInside(appExternalFile(""), file)
    }

    private fun isInside(root: File, child: File): Boolean {
        val rootPath = root.canonicalFile.toPath()
        val childPath = child.canonicalFile.toPath()
        return childPath.startsWith(rootPath)
    }

    private fun jsonEscape(text: String): String {
        return buildString {
            text.forEach { ch ->
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(ch)
                }
            }
        }
    }

    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }

    private data class ImportedRefs(
        var fontPath: String? = null,
        var themePath: String? = null
    )
}
